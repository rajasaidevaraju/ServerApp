package server.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import database.AppDatabase
import fi.iki.elonen.NanoHTTPD
import helpers.FileHandlerHelper
import java.io.IOException


class FileService {

    private val mimeType = "video/mp4"

    fun getFreeInternalMemorySize(): Long {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val freeSpace = availableBlocks * blockSize
        //val freeSpaceInMB = freeSpace / (1024 * 1024)
        return freeSpace
    }

    fun scanFolder(selectedDirectoryUri: Uri, fileHandlerHelper: FileHandlerHelper, database:AppDatabase): List<Long> {
        val fileMetas = fileHandlerHelper.getAllFilesMetaInDirectory(selectedDirectoryUri)
        val rows=database.fileDao().insertFiles(fileMetas)
        return rows
    }

    fun streamFile(fileId: Long, context: Context, database: AppDatabase, headers: Map<String, String>): NanoHTTPD.Response {

        val fileMeta = database.fileDao().getFileById(fileId)
        val file = fileMeta?.let { DocumentFile.fromTreeUri(context, it.fileUri) }

        val fileNotFoundResponse = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "text/plain",
            "File not found or inaccessible"
        )

        if (fileMeta == null || file == null || !file.isFile) {
            return fileNotFoundResponse
        }

        try {
            val fileLength = file.length()
            val rangeHeader = headers["range"]

            return if (rangeHeader != null && rangeHeader.startsWith("bytes=") ) {
                getPartialResponse(file, rangeHeader, fileLength, context)
            }else{
                getFullResponse(file, context)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Internal Server Error"
            )
        }
    }


    private fun getFullResponse(file: DocumentFile,context:Context): NanoHTTPD.Response {
        try {
            val inputStream = context.contentResolver.openInputStream(file.uri)
            val fileSize = file.length()
            val response = NanoHTTPD.newChunkedResponse(
                NanoHTTPD.Response.Status.OK,
                mimeType,
                inputStream
            )
            response.addHeader("Content-Type", mimeType)
            response.addHeader("Content-Length", fileSize.toString())
            return response
        }catch (e: Exception) {
            // Handle exceptions or errors while accessing the file
            e.printStackTrace()
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Internal Server Error"
            )
        }
    }


    private fun getPartialResponse(file: DocumentFile, rangeHeader: String, fileLength: Long, context:Context): NanoHTTPD.Response {

        try {

            val inputStream = context.contentResolver.openInputStream(file.uri)
            val rangeValue = rangeHeader.trim().substring("bytes=".length)
            val start: Long
            var end: Long
            if (rangeValue.startsWith("-")) {
                end = fileLength - 1
                start = (fileLength - 1
                        - rangeValue.substring("-".length).toLong())
            } else {
                val range = rangeValue.split("-".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                start = range[0].toLong()
                end = if (range.size > 1) range[1].toLong() else fileLength - 1
            }
            if (end > fileLength - 1) {
                end = fileLength - 1
            }
            if (inputStream != null) {
                inputStream.skip(start)
            }
            val contentLength = end - start + 1
            val response = NanoHTTPD.newChunkedResponse(
                NanoHTTPD.Response.Status.PARTIAL_CONTENT,
                mimeType,
                inputStream
            )
            Log.d("MKServer Video","INSIDE RETURN PARTIAL-RESPONSE and rangeHeader $rangeHeader")
            Log.d("MKServer Video"," Start:$start End:$end FileLength:$fileLength")
            response.addHeader("Content-Type", mimeType)
            response.addHeader("Content-Length", contentLength.toString())
            response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
            return response

        } catch (e: IOException) {
            e.printStackTrace()
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Internal Server Error"
            )

        }
    }

}