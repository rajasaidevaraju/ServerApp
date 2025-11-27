package server.controller

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import database.AppDatabase
import database.dao.FileMetaSimple
import database.entity.FileMeta
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import helpers.SharedPreferencesHelper
import server.service.DBService
import server.service.FileService
import server.service.NetworkService
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        return when {
            url.startsWith("/fileDetails") -> getFileDetails(url)
            url == "/files" -> getFiles(session)
            url == "/file" -> getFile(session)
            url == "/thumbnail" -> getThumbnail(session)
            url == "/name" -> getFileName(session)
            else -> notFound()
        }
    }

    private fun handlePostRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return when {
            url=="/file" -> uploadFile(session)
            url.startsWith("/file")&& url.endsWith("/performer") -> addPerformerToFile(url,session)
            url=="/thumbnail" -> updateThumbnail(session)
            url== "/scan" -> scanFolders(getSdCardURI(), getInternalURI())
            else -> notFound()
        }
    }

    private fun handlePutRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return when {
            url=="/repair" -> repairPath(getInternalURI(),getSdCardURI())
            isRenameUrl(url)-> renameFile(url,session)
            else -> notFound()
        }
    }

    private fun handleDeleteRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return when (url) {
            "/file" -> deleteFile(session)
            "/cleanup" -> cleanupDatabase()
            else -> notFound()
        }
    }

    private fun getFiles(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val page = params["page"]?.firstOrNull()
        val performerId=params["performerId"]?.firstOrNull()?.toLongOrNull()
        val sortBy=params["sortBy"]?.firstOrNull()?: "latest"
        var pageNo = 1
        val pageSize = 18

        if (!page.isNullOrBlank()) {
            pageNo = page.toIntOrNull() ?: 1
        }
        val offset = (pageNo - 1) * pageSize

        val orderClause = when (sortBy.lowercase()) {
            "latest" -> "ORDER BY file_meta.fileId DESC"
            "oldest" -> "ORDER BY file_meta.fileId ASC"
            "size-asc" -> "ORDER BY file_meta.file_size_bytes ASC"
            "size-desc" -> "ORDER BY file_meta.file_size_bytes DESC"
            "name-asc" -> "ORDER BY file_meta.file_name COLLATE NOCASE ASC"
            "name-desc" -> "ORDER BY file_meta.file_name COLLATE NOCASE DESC"
            else -> "ORDER BY file_meta.fileId DESC"
        }

        val baseQuery: String = if (performerId == null) {
            """
            SELECT file_meta.fileId AS fileId, file_meta.file_name AS fileName, file_meta.file_size_bytes AS fileSize
            FROM file_meta
            $orderClause
            LIMIT $pageSize OFFSET $offset
        """
        } else {
            """
            SELECT file_meta.fileId AS fileId, file_meta.file_name AS fileName, file_meta.file_size_bytes AS fileSize
            FROM file_meta
            JOIN video_actress_cross_ref ON file_meta.fileId = video_actress_cross_ref.fileId
            WHERE video_actress_cross_ref.actressId = $performerId
            $orderClause
            LIMIT $pageSize OFFSET $offset
        """
        }

        val query = SimpleSQLiteQuery(baseQuery)

        val paginatedFileData = if (performerId == null)
            fileDao.getFilesSorted(query)
        else
            fileDao.getFilesSortedForPerformer(query)

        val totalFiles = if (performerId == null)
            fileDao.getTotalFileCount()
        else
            database.videoActressCrossRefDao().getVideosForActress(performerId).size

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
        val jsonContent = gson.toJson(responseContent)
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, jsonContent)
    }

    private fun getFile(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val fileIdStr = params["fileId"]?.firstOrNull()

        if (fileIdStr.isNullOrBlank()) {
            return badRequest("Missing required parameter: fileId")
        }
        val isDownloadRequested=params["download"]?.firstOrNull()
        val downloadFlag=isDownloadRequested== "true";

        return try {
            val fileId = fileIdStr.toLong()
            fileService.streamFile(fileId, session.headers,downloadFlag)
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

        var destinationDir: File?
        var destinationFile: File? = null
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
            when (uploadTarget) {
                "external" -> {
                    val path=sdCardURI?.path
                    if (sdCardURI == null||path==null) {
                        return internalServerError(null, "External storage root folder not configured.")
                    }
                    val (_, freeExternal) = fileService.getExternalMemoryData()
                    if (freeExternal - contentLength < threeGBInBytes) {
                        return insufficientStorage()
                    }
                    destinationDir = File(path)
                }
                "internal" -> {
                    val path=internalURI.path
                    if (path==null) {
                        return internalServerError(null, "External storage root folder not configured.")
                    }
                    val (_, freeInternal) = fileService.getInternalMemoryData()
                    if (freeInternal - contentLength < threeGBInBytes) {
                        return insufficientStorage()
                    }
                    destinationDir = File(path)
                }
                else -> {
                    return badRequest("Invalid upload target")
                }
            }
            if (!destinationDir.isDirectory) {
                return internalServerError(null, "No directory present")
            }

            val boundary = extractBoundary(session.headers)
            if (boundary == null) {
                return badRequest("No Boundary in headers")
            }
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneId.of("UTC"))
            val fileName = "defaultFIleName${formatter.format(Instant.now())}.mp4"
            destinationFile = File(destinationDir,fileName)
            val destinationFileURI = Uri.fromFile(destinationFile)
            val created=destinationFile.createNewFile()
            if (!created || destinationFileURI == null) {
                return internalServerError(null, "File creation failed")
            }
            val destinationOutputStream = FileOutputStream(destinationFile)

            val originalFileName = networkService.processMultipartFormData(
                session.inputStream, boundary, destinationOutputStream, contentLength)


            var newFile = File(destinationDir, originalFileName)
            var count = 1
            val nameWithoutExt = newFile.nameWithoutExtension
            val ext = newFile.extension
            while (newFile.exists()||database.fileDao().isFileNamePresent(newFile.name)) {
                val newName = "$nameWithoutExt ($count)${if (ext.isNotEmpty()) ".$ext" else ""}"
                newFile = File(destinationDir, newName)
                count++
            }
            destinationFile.renameTo(newFile)
            destinationFile=newFile

            val finalFileName = destinationFile.name
            val uri=Uri.fromFile(destinationFile)
            val fileSize=destinationFile.length()
            val fileMeta = FileMeta(fileName = finalFileName, fileUri = uri, fileSize = fileSize)
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
        return try {
            val result=fileService.renameFile(fileId,newName,context)
            if(result.success){
                okRequest(result.message)
            }else{
                badRequest(result.message)
            }
        }catch (exception: Exception){
            internalServerError(exception,"Could not rename file")
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
        val contentType= headers["content-type"]
        // example of contentType multipart/form-data; boundary=----WebKitFormBoundaryuRqy8BtHu1nT2dfA
        val regex = "boundary=(.+)".toRegex()
        return contentType?.let { regex.find(it)?.groups?.get(1)?.value }
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
        return try {
            val uri = url.split("/")
            val fileId =uri[uri.size-1].toLongOrNull()
            val result=fileService.getFileDetails(fileId)
            if(result.success){
                newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, result.message)
            }else{
                badRequest(result.message)
            }

        }catch (exception: Exception) {
            internalServerError(exception,"Could not fetch file details")
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
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, jsonContent)
        } catch (exception: Exception) {
            return internalServerError(exception,"Thumbnail insert or update operation failed")
        }
    }

    private fun scanFolders(sdCardURI: Uri?, internalURI: Uri?): NanoHTTPD.Response {

        if(internalURI==null){
            return internalServerError(null,"Choose a root folder")
        }

        val rows: MutableList<Long> = mutableListOf()
        if (sdCardURI != null) {
            rows.addAll(fileService.scanFolder(sdCardURI))
        }

        rows.addAll(fileService.scanFolder(internalURI))

        val notInsertedRows = rows.count { it == -1L }
        val insertedRows = rows.size - notInsertedRows
        if(insertedRows==0){
            return okRequest("No new files found")
        }
        return okRequest("${rows.size - notInsertedRows} files inserted successfully")
    }

    private fun repairPath(internalURI: Uri?, sdCardURI: Uri?): NanoHTTPD.Response {
        try {
            if(internalURI==null){
                return internalServerError(null,"Choose a root folder")
            }
            val result=fileService.repairPath(internalURI, sdCardURI)
            fileService.syncFileSizesWithStorage()
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
            val path=fileMeta?.fileUri?.path

            if (path == null) {
                return notFound("File not found or inaccessible")
            }
            val file = File(path)
            if(!file.exists()){
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
        val filesInStorage=fileService.getFileNamesInStorage()
        val removedEntries = dbService.removeAbsentEntries(filesInStorage)
        if(removedEntries==0){
            return okRequest("No entries removed")
        }
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
