package helpers

import android.content.Context
import android.content.Context.STORAGE_SERVICE
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import database.entity.FileMeta
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.File


class FileHandlerHelper(private val context: Context){

    private val invalidChars = Regex("[<>:\"/\\\\|?*\\x00-\\x1F]")
    private val validExtensions = setOf("mp4", "avi", "mov", "mkv", "webm")

    fun isSdCardAvailable():Boolean{
        var sdCardAvailable = false
        val  storageManager = context.getSystemService(STORAGE_SERVICE) as StorageManager;
        val storageVolumes = storageManager.storageVolumes;
        for (volume in storageVolumes) {
            val description = volume.getDescription(context)
            if (volume.isRemovable && volume.state == Environment.MEDIA_MOUNTED && description.contains("SD")) {
                sdCardAvailable = true
                break
            }
        }

        return sdCardAvailable
    }

    fun getInternalVolume(): StorageVolume? {
        val storageManager = context.getSystemService(STORAGE_SERVICE) as StorageManager;
        val storageVolume = storageManager.primaryStorageVolume;
        return storageVolume
    }

    fun getExternalVolume(): StorageVolume?{
        val  storageManager = context.getSystemService(STORAGE_SERVICE) as StorageManager;
        val storageVolumes = storageManager.storageVolumes;
        for (volume in storageVolumes) {
            val description = volume.getDescription(context)
            if (volume.isRemovable && volume.state == Environment.MEDIA_MOUNTED && description.contains("SD")) {
                return volume
            }
        }
        return null
    }

    fun isValidFilename(name: String?): Boolean {
        if (name.isNullOrBlank()) {
            return false
        }

        if (name.length > 240) {
            return false
        }

        if (invalidChars.containsMatchIn(name)) {
            return false
        }
        return true
    }

    fun getAllFilesMetaInDirectory(uri: Uri): List<FileMeta> {
        val path=uri.path
        if(path==null){
            return emptyList()
        }
        val root = File(path)

        if(!root.exists()){
            return emptyList()
        }
        val fileMetas = mutableListOf<FileMeta>()
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val files=dir.listFiles()?: arrayOf()
            for (file in files) {
                val name=file.name
                val uri=Uri.fromFile(file)
                if(name.isNullOrBlank()){
                    continue
                }
                if (file.isDirectory) {
                    queue.add(file)
                } else {
                    if (isValidVideoFileName(name)) {
                        fileMetas.add(FileMeta(fileName = name, fileUri = uri))
                    }
                }
            }
        }

        return fileMetas
    }

    fun isValidVideoFileName(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return extension in validExtensions
    }

    fun serveStaticFile(filePath: String): NanoHTTPD.Response? {
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open("web/$filePath")
            val fileBytes = inputStream.readBytes()
            val mimeType = getMimeType(filePath)
            newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType, fileBytes.inputStream(), fileBytes.size.toLong())
        } catch (e: Exception) {
            Log.e("MKHttpServer", "Error serving file: $filePath", e)
            null
        }
    }

    // Helper to determine MIME type based on file extension
    private fun getMimeType(filePath: String): String {
        return when {
            filePath.endsWith(".html") -> "text/html"
            filePath.endsWith(".svg") -> "image/svg+xml"
            filePath.endsWith(".css") -> "text/css"
            filePath.endsWith(".js") -> "application/javascript"
            filePath.endsWith(".png") -> "image/png"
            filePath.endsWith(".jpg") || filePath.endsWith(".jpeg") -> "image/jpeg"
            filePath.endsWith(".gif") -> "image/gif"
            else -> "application/octet-stream"
        }
    }

    fun getExternalSdCardPath(): File? {
        val externalDirs = context.getExternalFilesDirs(null)
        for (file in externalDirs) {
            if (file != null && Environment.isExternalStorageRemovable(file)) {
                val path = file.absolutePath.split("/Android")[0]
                return File(path)
            }
        }
        return null
    }



}