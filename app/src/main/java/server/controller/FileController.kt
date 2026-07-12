package server.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import database.AppDatabase
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
    private val router = Router()

    init {
        router.get("/fileDetails/{fileId}") { params, _ -> getFileDetails(params.longPathParam("fileId")) }
        router.get("/files") { _, session -> getFiles(session) }
        router.get("/file") { _, session -> getFile(session) }
        router.get("/thumbnail") { _, session -> getThumbnail(session) }
        router.get("/name") { _, session -> getFileName(session) }
        router.get("/file/status") { _, session -> getChunkedUploadStatus(session) }

        router.post("/file") { _, session -> uploadFile(session) }
        router.post("/file/{fileId}/performer") { params, session -> addPerformerToFile(params.longPathParam("fileId"), session) }
        router.post("/thumbnail") { _, session -> updateThumbnail(session) }
        router.post("/thumbnail/extract") { _, session -> extractThumbnail(session) }
        router.post("/scan") { _, _ -> scanFolders(getSdCardURI(), getInternalURI()) }
        router.post("/file/chunk") { _, session -> uploadChunk(session) }
        router.post("/file/upload/chunk") { _, session -> uploadChunk(session) }

        router.put("/repair") { _, _ -> repairPath(getInternalURI(), getSdCardURI()) }
        router.put("/file/{fileId}/rename") { params, session -> renameFile(params.longPathParam("fileId"), session) }

        router.delete("/file/{fileId}/performer/{performerId}") { params, _ ->
            removePerformerFromFile(params.longPathParam("fileId"), params.longPathParam("performerId"))
        }
        router.delete("/file") { _, session -> deleteFile(session) }
        router.delete("/cleanup") { _, _ -> cleanupDatabase() }
    }

    override fun handleRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            router.handle(url, session) ?: notFound()
        } catch (e: BadRequestException) {
            badRequest(e.message ?: "The request could not be processed due to invalid syntax")
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

        // orderClause is safe to interpolate: it comes from the whitelist above
        val query = if (performerId == null) {
            SimpleSQLiteQuery(
                """
                SELECT file_meta.fileId AS fileId, file_meta.file_name AS fileName, file_meta.file_size_bytes AS fileSize, file_meta.duration_ms as durationMs
                FROM file_meta
                $orderClause
                LIMIT ? OFFSET ?
                """,
                arrayOf<Any>(pageSize, offset)
            )
        } else {
            SimpleSQLiteQuery(
                """
                SELECT file_meta.fileId AS fileId, file_meta.file_name AS fileName, file_meta.file_size_bytes AS fileSize, file_meta.duration_ms as durationMs
                FROM file_meta
                JOIN video_actress_cross_ref ON file_meta.fileId = video_actress_cross_ref.fileId
                WHERE video_actress_cross_ref.actressId = ?
                $orderClause
                LIMIT ? OFFSET ?
                """,
                arrayOf<Any>(performerId, pageSize, offset)
            )
        }

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
            val fileName = "defaultFileName${formatter.format(Instant.now())}.mp4"
            destinationFile = File(destinationDir,fileName)
            val destinationFileURI = Uri.fromFile(destinationFile)
            val created=destinationFile.createNewFile()
            if (!created || destinationFileURI == null) {
                return internalServerError(null, "File creation failed")
            }
            val originalFileName = FileOutputStream(destinationFile).use { destinationOutputStream ->
                networkService.processMultipartFormData(
                    session.inputStream, boundary, destinationOutputStream, contentLength)
            }


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
            val durationMs=fileService.getMediaDuration(destinationFile)
            val fileMeta = FileMeta(fileName = finalFileName, fileUri = uri, fileSize = fileSize, durationMs=durationMs)
            database.fileDao().insertFile(fileMeta)
            return okRequest("File Stored Successfully")
        } catch (exception: Exception) {
            destinationFile?.delete()
            val message = exception.message ?: "Could not complete file upload"
            return internalServerError(exception, message)
        }
    }


    fun renameFile(fileId: Long, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val postBody=parseSmallRequestBody(session)
        val newName=postBody["newName"]
        if(newName.isNullOrBlank()){
            return badRequest("Missing required parameter: newName")
        }
        return try {
            val result=fileService.renameFile(fileId, newName)
            if(result.success){
                okRequest(result.message)
            }else{
                badRequest(result.message)
            }
        }catch (exception: Exception){
            internalServerError(exception,"Could not rename file")
        }
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
            var thumbnail = database.fileDao().getScreenshotDataBinary(fileId)
            if (thumbnail == null) {
                thumbnail = ByteArray(0)
            }
            newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                MIME_JPEG,
                thumbnail.inputStream(),
                thumbnail.size.toLong()
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
            val fileName = database.fileDao().getFileNameById(fileId)
            if (fileName == null) {
                return badRequest("file ID $fileId not present")
            }
            newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                MIME_JSON,
                gson.toJson(mapOf("fileName" to fileName))
            )
        } catch (exception: NumberFormatException) {
            badRequest("Invalid fileId")
        } catch (exception: Exception) {
            internalServerError(exception,"Name retrieval failed for fileId: $fileIdStr")
        }
    }

    private fun getFileDetails(fileId: Long): NanoHTTPD.Response{
        return try {
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

    private fun updateThumbnail( session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {

        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)

            val fileIdStr = session.parameters["fileId"]?.firstOrNull() ?: return badRequest("Missing required parameter: fileId")
            val fileId = fileIdStr.toLong()
            val tempFilePath = files["image"] ?: return badRequest("Missing image file")
            val imageBytes = File(tempFilePath).readBytes()

            if (imageBytes.isEmpty()) {
                return badRequest("Empty image file")
            }

            database.fileDao().updateScreenshotBinary(fileId = fileId, binary = imageBytes)

            val responseContent = mapOf(
                "message" to "Thumbnail updated successfully",
                "fileId" to fileId
            )
            newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                MIME_JSON,
                gson.toJson(responseContent)
            )

        } catch (e: Exception) {
            internalServerError(e, "Thumbnail binary insert failed")
        }
    }

    private fun extractThumbnail(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            
            val params = session.parameters
            val fileIdStr = params["fileId"]?.firstOrNull() ?: return badRequest("Missing required parameter: fileId")
            val timestampStr = params["timestamp"]?.firstOrNull() ?: return badRequest("Missing required parameter: timestamp")
            
            val fileId = fileIdStr.toLong()
            val timestamp = timestampStr.toLong()
            
            val result = fileService.extractAndSaveThumbnail(fileId, timestamp)
            if (result.success) {
                val thumbnail = database.fileDao().getScreenshotDataBinary(fileId) ?: ByteArray(0)
                newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    MIME_JPEG,
                    thumbnail.inputStream(),
                    thumbnail.size.toLong()
                )
            } else {
                badRequest(result.message)
            }
        } catch (exception: Exception) {
            internalServerError(exception, "Could not extract thumbnail")
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
            fileService.populateMissingDurations()
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

    private fun addPerformerToFile(fileId: Long, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val postBody=HashMap<String,String>()
        session.parseBody(postBody)
        val postData=postBody["postData"]
        val result=fileService.addPerformerToFile(postData,fileId)

        return if(result.success){
            okRequest(result.message)
        }else{
            badRequest(result.message)
        }
    }

    private fun removePerformerFromFile(fileId: Long, performerId: Long): NanoHTTPD.Response {
        val result = fileService.removePerformerFromFile(fileId, performerId)

        return if (result.success) {
            okRequest(result.message)
        } else {
            badRequest(result.message)
        }
    }

    private fun getChunkedUploadStatus(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            val params = session.parameters
            val fileName = params["fileName"]?.firstOrNull() ?: return badRequest("Missing fileName")
            val fileSize = params["fileSize"]?.firstOrNull()?.toLongOrNull() ?: return badRequest("Missing or invalid fileSize")
            val chunkSize = params["chunkSize"]?.firstOrNull()?.toLongOrNull() ?: return badRequest("Missing or invalid chunkSize")
            val target = params["target"]?.firstOrNull() ?: return badRequest("Missing target")


            val result = fileService.getChunkedUploadStatus(fileName, fileSize, chunkSize, target)

            if (result.has("error") && result.getString("error") == "FILE_NAME_CONFLICT") {
                return newFixedLengthResponse(NanoHTTPD.Response.Status.CONFLICT, MIME_JSON, result.toString())
            } else if (result.has("error")) {
                return newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_JSON, result.toString())
            }

            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, result.toString())
        } catch (e: Exception) {
            internalServerError(e, "Status check failed")
        }
    }

    private fun uploadChunk(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val chunkIndex = params["chunkIndex"]?.firstOrNull()?.toIntOrNull() ?: return badRequest("Missing chunkIndex")
        val fileName = params["fileName"]?.firstOrNull() ?: return badRequest("Missing fileName")
        val fileSize = params["fileSize"]?.firstOrNull()?.toLongOrNull() ?: return badRequest("Missing fileSize")
        val target = params["target"]?.firstOrNull() ?: return badRequest("Missing target")

        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: return badRequest("Missing content-length")

        return try {
            val chunkData = ByteArray(contentLength)
            var totalRead = 0
            val input = session.inputStream
            while (totalRead < contentLength) {
                val read = input.read(chunkData, totalRead, contentLength - totalRead)
                if (read == -1) {
                    break
                }
                totalRead += read
            }

            if (totalRead < contentLength) {
                return badRequest("Incomplete chunk data. Expected $contentLength, got $totalRead")
            }
            
            val result = fileService.handleChunkUpload(chunkIndex, fileName, fileSize, target, chunkData)
            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, result)
        } catch (e: Exception) {
            internalServerError(e, e.message ?: "Chunk upload failed")
        }
    }
}