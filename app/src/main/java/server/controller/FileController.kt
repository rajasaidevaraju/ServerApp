package server.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import database.AppDatabase
import database.entity.FileMeta
import database.entity.SimplifiedFileMeta
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import helpers.SharedPreferencesHelper
import server.service.DBService
import server.service.FileService
import server.service.NetworkService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

class FileController(private val context: Context,
                     private val database: AppDatabase, private val fileService: FileService,
                     private val networkService: NetworkService,
                     private val dbService: DBService,  prefHandler: SharedPreferencesHelper
) : BaseController(prefHandler) {

    private val fileDao = database.fileDao()
    override fun handleRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return when (session.method) {
            NanoHTTPD.Method.GET -> handleGetRequest(url, session)
            NanoHTTPD.Method.POST -> handlePostRequest(url, session)
            NanoHTTPD.Method.PUT -> handlePutRequest(url, session)
            NanoHTTPD.Method.DELETE -> handleDeleteRequest(url, session)
            else -> notFound()
        }
    }
    private fun handleGetRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        when {
            url.startsWith("/fileDetails") -> return getFileDetails(url)
            url == "/files" -> return getFiles(session)
            url == "/file" -> return getFile(session)
            url == "/thumbnail" -> return getThumbnail(session)
            url == "/name" -> return getFileName(session)
            else -> return notFound()
        }
    }

    private fun handlePostRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        when {
            url=="/file" -> return uploadFile(session)
            url.startsWith("/file")&& url.endsWith("/performer") -> return addPerformerToFile(url,session)
            url=="/thumbnail" -> return updateThumbnail(session)
            url== "/scan" -> return scanFolders(getSdCardURI(), getInternalURI())
            else -> return notFound()
        }
    }

    private fun handlePutRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        when {
            url=="/repair" -> return repairPath(getInternalURI(),getSdCardURI())
            isRenameUrl(url)-> return renameFile(url,session)
            else -> return notFound()
        }
    }

    private fun handleDeleteRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        when (url) {
            "/file" -> return deleteFile(session)
            "/cleanup" -> return cleanupDatabase()
            else -> return notFound()
        }
    }

    private fun getFiles(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val page = params["page"]?.firstOrNull()
        val performerId=params["performerId"]?.firstOrNull()?.toLongOrNull()
        var pageNo = 1
        val pageSize = 18
        if (!page.isNullOrBlank()) {
            pageNo = page.toIntOrNull() ?: 1
        }
        val offset = (pageNo - 1) * pageSize

        val paginatedFileData:List<SimplifiedFileMeta>
        if(performerId==null){
            paginatedFileData=fileDao.getFilesPaginated(offset, pageSize)
        }else{
            val actress=database.actressDao().getActressById(performerId)
            if(actress==null){
                return notFound("performer with id $performerId not found")
            }
            paginatedFileData=fileDao.getFilesWithPerformerPaginated(offset, pageSize,performerId)
        }
        val totalFiles:Int = if(performerId==null) {
            fileDao.getTotalFileCount()
        } else{
            database.videoActressCrossRefDao().getVideosForActress(performerId).size
        }

        if (paginatedFileData.isEmpty()) {
            return notFound("No files found")
        }
        val responseContent = mapOf(
            "data" to paginatedFileData,
            "meta" to mapOf(
                "page" to pageNo,
                "limit" to pageSize,
                "total" to totalFiles
            )
        )
        val jsonContent: String = gson.toJson(responseContent)
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, jsonContent)
    }

    private fun getFile(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val fileIdStr = params["fileId"]?.firstOrNull()

        if (fileIdStr.isNullOrBlank()) {
            return badRequest("Missing required parameter: fileId")
        }
        return try {
            val fileId = fileIdStr.toLong()
            fileService.streamFile(fileId, context, session.headers)
        } catch (exception: Exception) {
            internalServerError(exception,"Invalid File ID")
        }
    }

    private fun uploadFile(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val internalURI = getInternalURI()
        val sdCardURI = getSdCardURI()

        if (internalURI == null) {
            return internalServerError(null, "Internal storage root folder not configured.")
        }

        var destinationDir: DocumentFile? = null
        var destinationFile: DocumentFile? = null
        var uploadTarget = "internal"
        try {
            val params = session.parameters
            val contentLength = session.headers["content-length"]?.toLong()
            if (contentLength == null) {
                return badRequest("Content-Length header missing")
            }
            val threeGBInBytes = 3L * 1024 * 1024 * 1024

            val target = params["uploadTarget"]?.firstOrNull()
            if (target != null) {
                uploadTarget = target
            }
            if (uploadTarget == "external") {
                if (sdCardURI == null) {
                    return internalServerError(null, "External storage root folder not configured.")
                }
                val (_, freeExternal) = fileService.getExternalMemoryData()
                if (freeExternal - contentLength < threeGBInBytes) {
                    return insufficientStorage()
                }
                destinationDir = DocumentFile.fromTreeUri(context, sdCardURI)
            } else if (uploadTarget == "internal") {
                val (_, freeInternal) = fileService.getInternalMemoryData()
                if (freeInternal - contentLength < threeGBInBytes) {
                    return insufficientStorage()
                }
                destinationDir = DocumentFile.fromTreeUri(context, internalURI)
            } else {
                return badRequest("Invalid upload target")
            }
            if (destinationDir == null || destinationDir.isFile) {
                return internalServerError(null, "Could not store the file")
            }

            val boundary = extractBoundary(session.headers)
            if (boundary == null) {
                return badRequest("No Boundary in headers")
            }
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneId.of("UTC"))
            val fileName = "defaultFIleName${formatter.format(Instant.now())}.mp4"
            destinationFile = destinationDir.createFile("video/mp4", fileName)
            val destinationFileURI = destinationFile?.uri
            if (destinationFile == null || destinationFileURI == null) {
                return internalServerError(null, "File creation failed")
            }
            val destinationOutputStream = context.contentResolver.openOutputStream(destinationFileURI)
            if (destinationOutputStream == null) {
                return internalServerError(null, "Could not write to file")
            }
            val originalFileName = networkService.processMultipartFormData(
                session.inputStream,
                boundary,
                destinationOutputStream,
                contentLength,
                destinationDir
            )
            var renameOp = false
            if (!database.fileDao().isFileNamePresent(originalFileName)) {
                renameOp = destinationFile.renameTo(originalFileName)
            }
            var finalFileName = originalFileName
            if (!renameOp) {
                var i = 1
                finalFileName = insertStringBeforeExtension(originalFileName, " ($i)")
                while (destinationDir.findFile(finalFileName) != null || database.fileDao().isFileNamePresent(finalFileName)) {
                    i += 1
                    finalFileName = insertStringBeforeExtension(originalFileName, " ($i)")
                    if (i == 10) {
                        finalFileName = fileName
                        break
                    }
                }
                destinationFile.renameTo(finalFileName)
            }

            val fileMeta = FileMeta(fileName = finalFileName, fileUri = destinationFile.uri)
            database.fileDao().insertFile(fileMeta)
            return okRequest("File Stored Successfully")
        } catch (exception: Exception) {
            destinationFile?.delete()
            val message = exception.message ?: "Could not complete file upload"
            return internalServerError(exception, message)
        }
    }

    fun renameFile(url:String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val fileId=extractFileIdFromUrl(url)
        if(fileId==null){
            return badRequest("Invalid URL")
        }
        val postBody=parseSmallRequestBody(session)
        val newName=postBody["newName"]
        if(newName.isNullOrBlank()){
            return badRequest("Missing required parameter: newName")
        }
        try {
            val result=fileService.renameFile(fileId,newName,context)
            if(result.success){
                return okRequest(result.message)
            }else{
                return badRequest(result.message)
            }
        }catch (exception: Exception){
            return internalServerError(exception,"Could not rename file")
        }
    }

    fun isRenameUrl(url: String): Boolean {
        val regex = """/file/\d+/rename""".toRegex()
        return regex.matches(url)
    }

    fun extractFileIdFromUrl(url: String): Long? {
        val regex = """/file/(\d+)/rename""".toRegex()
        val matchResult = regex.find(url)

        return matchResult?.groupValues?.get(1)?.toLongOrNull()
    }


    private fun extractBoundary(headers:Map<String, String>):String?{
        val contentType=headers.get("content-type")
        // example of contentType multipart/form-data; boundary=----WebKitFormBoundaryuRqy8BtHu1nT2dfA
        val regex = "boundary=(.+)".toRegex()
        return contentType?.let { regex.find(it)?.groups?.get(1)?.value }
    }

    private fun insertStringBeforeExtension(fileName: String, insertString: String):String{
        val regex = Regex("(\\.[^\\.]+)$")
        val matchResult = regex.find(fileName)
        return if (matchResult != null) {
            val index = matchResult.range.first
            fileName.substring(0, index) + insertString + fileName.substring(index)
        } else {
            fileName
        }
    }

    private fun getThumbnail(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val fileIdStr = params["fileId"]?.firstOrNull()

        if (fileIdStr.isNullOrBlank()) {
            return badRequest("Missing required parameter: fileId")
        }
        return try {
            val fileId = fileIdStr.toLong()
            var thumbnail = database.fileDao().getScreenshotData(fileId)
            var exists = true
            if (thumbnail == null) {
                thumbnail = ""
                exists = false
            }
            val responseContent = mapOf("imageData" to thumbnail, "exists" to exists)
            newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                MIME_JSON,
                gson.toJson(responseContent)
            )
        } catch (exception: Exception) {
            internalServerError(exception,"Could not get thumbnail for id: $fileIdStr")
        }
    }

    private fun getFileName(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val fileIdStr = params["fileId"]?.firstOrNull()

        if (fileIdStr.isNullOrBlank()) {
            return badRequest("Missing required parameter: fileId")
        }
        return try {
            val fileId = fileIdStr.toLong()
            val fileMeta = database.fileDao().getFileById(fileId)
            if (fileMeta == null) {
                return badRequest("file ID $fileId not present")
            }
            newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                MIME_JSON,
                gson.toJson(mapOf("fileName" to fileMeta.fileName))
            )
        } catch (exception: NumberFormatException) {
            badRequest("Invalid fileId")
        } catch (exception: Exception) {
            internalServerError(exception,"Name retrieval failed for fileId: $fileIdStr")
        }
    }

    private fun getFileDetails(url: String): NanoHTTPD.Response{
        try {
            val uri = url.split("/")
            val fileId =uri[uri.size-1].toLongOrNull()
            val result=fileService.getFileDetails(fileId)
            if(result.success){
                return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, result.message)
            }else{
                return badRequest(result.message)
            }

        }catch (exception: Exception) {
            return internalServerError(exception,"Could not fetch file details")
        }
    }

    private fun updateThumbnail(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        try {
            val postBody = HashMap<String, String>()
            session.parseBody(postBody)
            val postData = postBody["postData"]
            if (postData.isNullOrBlank()) {
                return badRequest("Missing or empty postData")
            }
            val insertedFileId = dbService.insertScreenshotData(postData, database.fileDao())
            val responseContent = mapOf(
                "message" to "Thumbnail inserted or updated for file with ID $insertedFileId",
                "fileId" to insertedFileId
            )
            val jsonContent: String = gson.toJson(responseContent)
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, jsonContent)
        } catch (exception: Exception) {
            return internalServerError(exception,"Thumbnail insert or update operation failed")
        }
    }

    private fun scanFolders(sdCardURI: Uri?, internalURI: Uri?): NanoHTTPD.Response {
        var sdCardScanTime: Long = 0
        var internalScanTime: Long = 0
        val tag="ScanTime"
        if(internalURI==null){
            return internalServerError(null,"Choose a root folder")
        }

        val rows: MutableList<Long> = mutableListOf()
        if (sdCardURI != null) {
            Log.d(tag, "SD Card scan begins")
            sdCardScanTime = measureTimeMillis {
                rows.addAll(fileService.scanFolder(sdCardURI))
            }
            Log.d(tag, "SD Card scan took: $sdCardScanTime ms")
        }

        Log.d(tag, "Internal storage scan begins")
        internalScanTime = measureTimeMillis {
            rows.addAll(fileService.scanFolder(internalURI))
        }
        Log.d(tag, "Internal storage scan took: $internalScanTime ms")
        val notInsertedRows = rows.count { it == -1L }

        return okRequest("${rows.size - notInsertedRows} files inserted successfully")
    }

    private fun repairPath(internalURI: Uri?, sdCardURI: Uri?): NanoHTTPD.Response {
        try {
            if(internalURI==null){
                return internalServerError(null,"Choose a root folder")
            }
            val result=fileService.repairPath(internalURI, sdCardURI)
            return okRequest(result.message)
        } catch (e: Exception) {
            return internalServerError(e,"Repair operation failed")
        }
    }
    private fun deleteFile(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val fileIdStr = params["fileId"]?.firstOrNull()

        if (fileIdStr.isNullOrBlank()) {
           return badRequest("Missing required parameter: fileId")
        }
        try {
            val fileId = fileIdStr.toLong()
            val fileMeta = database.fileDao().getFileById(fileId)
            val file = fileMeta?.let { DocumentFile.fromTreeUri(context, it.fileUri) }
            if (fileMeta == null || file == null || !file.isFile) {
                return notFound("File not found or inaccessible")
            }
            val isDeleted = file.delete()
            if (isDeleted) {
                database.fileDao().deleteFile(fileMeta)
                return okRequest("File deleted successfully")
            } else {
                return internalServerError(null, "Could not delete file for id: $fileIdStr")
            }
        } catch (exception: Exception) {
            return internalServerError(exception, "Could not delete file for id: $fileIdStr")
        }
    }

    private fun cleanupDatabase(): NanoHTTPD.Response {
        val filesInStorage=fileService.getFileNamesInStorage();
        val removedEntries = dbService.removeAbsentEntries(filesInStorage)
        val responseContent = mapOf(
            "message" to "$removedEntries rows removed",
            "rows_deleted" to removedEntries
        )
        val jsonContent: String = gson.toJson(responseContent)
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, jsonContent)
    }

    //example url POST /server/file/{fileId}/performers
    private fun addPerformerToFile(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val postBody=HashMap<String,String>()
        session.parseBody(postBody)
        val postData=postBody["postData"]
        val uri = url.split("/")
        val fileId =uri[uri.size-2].toLongOrNull()
        val result=fileService.addPerformerToFile(postData,fileId)

        return if(result.success){
            okRequest(result.message)
        }else{
            badRequest(result.message)
        }
    }
}
