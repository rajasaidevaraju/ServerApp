package server.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import database.AppDatabase
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import helpers.FileHandlerHelper
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream


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
        val gson: Gson = GsonBuilder().create()


        if (fileMeta == null || file == null || !file.isFile) {
            val responseContent= mapOf("message" to "File not found or inaccessible")
            val fileNotFoundResponse = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "application/json",
                gson.toJson(responseContent)
            )
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
            val responseContent= mapOf("message" to "Error when streaming file")
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "application/json",
                gson.toJson(responseContent)
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
            response.addHeader("Accept-Ranges", "bytes")
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

            // Open the file descriptor and get the file channel
            val pfd = context.contentResolver.openFileDescriptor(file.uri, "r")
            val fileDescriptor = pfd?.fileDescriptor
            val fileInputStream = FileInputStream(fileDescriptor)
            val fileChannel = fileInputStream.channel

            //val inputStream:InputStream = context.contentResolver.openInputStream(file.uri)
            //inputStream.skip(1)


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


            fileInputStream.skip(start)
            val contentLength = end - start + 1


            val response = NanoHTTPD.newChunkedResponse(
                Status.PARTIAL_CONTENT,
                mimeType,
                fileInputStream
            )
            Log.d("MKServer Video","INSIDE RETURN PARTIAL-RESPONSE and rangeHeader $rangeHeader")
            Log.d("MKServer Video"," Start:$start End:$end FileLength:$fileLength")
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Length", contentLength.toString())
            response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
            return response

        } catch (e: Exception) {
            e.printStackTrace()
            val gson: Gson = GsonBuilder().create()
            val responseContent = mapOf("message" to "Error while seeking for file")
            val jsonContent: String = gson.toJson(responseContent)
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json",jsonContent )
        }
    }

}