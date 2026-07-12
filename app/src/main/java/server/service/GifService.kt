package server.service

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import database.AppDatabase
import helpers.GifEncoder
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class GifGenerationException(message: String) : Exception(message)

/**
 * Generates short animated GIF previews from video files. Optimized for
 * latency: frames are decoded at target resolution from sync (key) frames
 * only, extraction and encoding both fan out across a shared thread pool,
 * and finished GIFs are kept in a small in-memory LRU cache.
 */
class GifService(database: AppDatabase) {

    private val fileDao = database.fileDao()

    private val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
    private val executor = Executors.newFixedThreadPool(workerCount) { runnable ->
        Thread(runnable, "gif-worker").apply { isDaemon = true }
    }

    private val maxCacheBytes = 16L * 1024 * 1024
    private var cacheBytes = 0L
    private val cache = LinkedHashMap<String, ByteArray>(16, 0.75f, true)

    fun generateGif(fileId: Long, width: Int, frameCount: Int, delayMs: Int): ByteArray {
        val cacheKey = "$fileId:$width:$frameCount:$delayMs"
        cacheGet(cacheKey)?.let { return it }

        val fileMeta = fileDao.getFileById(fileId)
            ?: throw GifGenerationException("File with id $fileId not found")
        val filePath = fileMeta.fileUri.path
            ?: throw GifGenerationException("File not found in the file system")
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw GifGenerationException("File not found in the file system")
        }

        val probe = probeVideo(file, fileMeta.durationMs)
        val targetWidth = width.coerceAtMost(probe.width)
        val targetHeight = (targetWidth.toLong() * probe.height / probe.width).toInt().coerceAtLeast(2)
        val timestampsUs = frameTimestampsUs(probe.durationMs, frameCount)

        val bitmaps = extractFramesParallel(file, timestampsUs, targetWidth, targetHeight)
        if (bitmaps.isEmpty()) {
            throw GifGenerationException("Could not decode any frames from video")
        }

        try {
            val encoded = executor.invokeAll(bitmaps.map { bitmap ->
                Callable { GifEncoder.encodeFrame(bitmap) }
            }).map { it.get() }
            val gif = GifEncoder.assemble(targetWidth, targetHeight, delayMs, encoded)
            cachePut(cacheKey, gif)
            return gif
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    private data class VideoProbe(val width: Int, val height: Int, val durationMs: Long)

    private fun probeVideo(file: File, knownDurationMs: Long): VideoProbe {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val durationMs = if (knownDurationMs > 0) {
                knownDurationMs
            } else {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
            if (rawWidth <= 0 || rawHeight <= 0 || durationMs <= 0) {
                throw GifGenerationException("File is not a readable video")
            }
            // decoded frames come out rotation-applied, so swap dimensions to match
            val rotated = rotation == 90 || rotation == 270
            return VideoProbe(
                width = if (rotated) rawHeight else rawWidth,
                height = if (rotated) rawWidth else rawHeight,
                durationMs = durationMs
            )
        } finally {
            retriever.release()
        }
    }

    private fun frameTimestampsUs(durationMs: Long, frameCount: Int): LongArray {
        // skip the first and last 5% to avoid intro/outro black frames
        val startUs = durationMs * 1000 / 20
        val spanUs = durationMs * 1000 * 18 / 20
        return LongArray(frameCount) { i ->
            if (frameCount == 1) startUs + spanUs / 2
            else startUs + spanUs * i / (frameCount - 1)
        }
    }

    /**
     * Splits the timestamps round-robin across workers; each worker reuses a
     * single retriever for its share, since setDataSource dominates per-frame
     * cost when opened repeatedly.
     */
    private fun extractFramesParallel(
        file: File,
        timestampsUs: LongArray,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap> {
        val workers = workerCount.coerceAtMost(timestampsUs.size)
        val frames = arrayOfNulls<Bitmap>(timestampsUs.size)

        executor.invokeAll((0 until workers).map { worker ->
            Callable {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    var index = worker
                    while (index < timestampsUs.size) {
                        val frame = retriever.getScaledFrameAtTime(
                            timestampsUs[index],
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            targetWidth,
                            targetHeight
                        )
                        frames[index] = frame?.let { exactSize(it, targetWidth, targetHeight) }
                        index += workers
                    }
                } finally {
                    retriever.release()
                }
            }
        }).forEach { it.get() }

        return frames.filterNotNull()
    }

    private fun exactSize(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) {
            return bitmap
        }
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }

    @Synchronized
    private fun cacheGet(key: String): ByteArray? = cache[key]

    @Synchronized
    private fun cachePut(key: String, gif: ByteArray) {
        if (gif.size > maxCacheBytes) {
            return
        }
        cache.remove(key)?.let { cacheBytes -= it.size }
        cache[key] = gif
        cacheBytes += gif.size
        val iterator = cache.entries.iterator()
        while (cacheBytes > maxCacheBytes && iterator.hasNext()) {
            cacheBytes -= iterator.next().value.size
            iterator.remove()
        }
    }
}
