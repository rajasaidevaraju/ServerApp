package server.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import android.net.Uri
import android.os.StatFs
import android.os.storage.StorageVolume
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import database.AppDatabase
import database.dao.FileDetails
import database.dao.Item
import database.entity.FileMeta
import database.entity.UploadProgress
import database.jointable.VideoActressCrossRef
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import helpers.FileHandlerHelper
import helpers.SharedPreferencesHelper
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import kotlin.math.ceil


class FileService(private val database: AppDatabase,private val fileHandlerHelper: FileHandlerHelper, private val prefHandler: SharedPreferencesHelper, private val context: Context) {

    private val mimeType = "video/mp4"
    private val fileDao=database.fileDao()
    private val uploadProgressDao = database.uploadProgressDao()

    fun parsePostData(postData: String?, paramName: String): String? {
        if (postData == null) {
            return null
        }
        val jsonBody = JSONObject(postData)
        if (!jsonBody.has(paramName)) {
            return null
        }
        return jsonBody.getString(paramName)
    }

    fun getInternalMemoryData():Pair<Long,Long>{
        val storageVolume=fileHandlerHelper.getInternalVolume()
        val total=getTotalMemorySize(storageVolume)
        val free=getFreeMemorySize(storageVolume)
        return Pair(total,free)
    }

    fun getExternalMemoryData():Pair<Long,Long> {
        val storageVolume = fileHandlerHelper.getExternalVolume()
        val total = getTotalMemorySize(storageVolume)
        val free = getFreeMemorySize(storageVolume)
        return Pair(total,free)
    }

    fun getFileNamesInStorage():HashSet<String>{
        val names=HashSet<String>();
        val internalURI=prefHandler.getInternalURI();
        if(internalURI!=null){
            val files=fileHandlerHelper.getAllFilesMetaInDirectory(internalURI)
            for(file in files){
                names.add(file.fileName)
            }
        }
        val sdCardURI=prefHandler.getSDCardURI();
        if(sdCardURI!=null){
            val files=fileHandlerHelper.getAllFilesMetaInDirectory(sdCardURI)
            for(file in files){
                names.add(file.fileName)
            }
        }
        return names
    }


    private fun getFreeMemorySize(storageVolume: StorageVolume?):Long{
        if(storageVolume==null){
            return 0
        }
        val path=storageVolume.directory
        if(path==null){
            return 0
        }
        val stat = StatFs(path.path)
        return stat.availableBytes
    }

    private fun getTotalMemorySize(storageVolume: StorageVolume?):Long{
        if(storageVolume==null){
            return 0
        }
        val path=storageVolume.directory
        if(path==null){
            return 0
        }
        val stat = StatFs(path.path)
        return stat.totalBytes
    }

    fun scanFolder(selectedDirectoryUri: Uri): List<Long> {
        val fileMetasFromDirectory=fileHandlerHelper.getAllFilesMetaInDirectory(selectedDirectoryUri)
        val fileMetaFromDb= fileDao.getAllFiles().toSet()
        val newFiles: MutableList<FileMeta> = mutableListOf()

        for(fileMeta in fileMetasFromDirectory){
            if(!fileMetaFromDb.contains(fileMeta)){
                val filePath = fileMeta.fileUri.path
                if (filePath != null) {
                    val file = File(filePath)
                    val duration=getMediaDuration(file)
                    val updatedFileMeta = fileMeta.copy(durationMs = duration)
                    newFiles.add(updatedFileMeta)
                }else{
                    newFiles.add(fileMeta)
                }
            }
        }
        return if (newFiles.isNotEmpty()) {
            fileDao.insertFiles(newFiles)
        } else {
            emptyList()
        }
    }

    fun addPerformerToFile(postData: String?,fileId:Long?):ServiceResult{
        if(fileId==null){
            return ServiceResult(success = false, message = "Missing ID")
        }
        val performerId=parsePostData(postData,"itemId")?.toLongOrNull()
        if(performerId==null){
            return ServiceResult(success = false, message = "itemId is missing or not valid")
        }

        val videoActressCrossRef= VideoActressCrossRef(fileId,performerId)
        val result=database.videoActressCrossRefDao().insertVideoActressCrossRef(videoActressCrossRef)

        if(result==-1L){
            return ServiceResult(success = false, message = "insert operation failed")
        }
        return ServiceResult(success = true, message = "Performer added successfully")
    }

    fun removePerformerFromFile(fileId: Long, performerId: Long): ServiceResult {

        val rowsDeleted = database.videoActressCrossRefDao().deleteVideoActressCrossRefByIDs(fileId, performerId)

        if (rowsDeleted == 0) {
            return ServiceResult(success = false, message = "Performer association not found or deletion failed")
        }
        return ServiceResult(success = true, message = "Performer removed successfully")
    }

    fun repairPath(internalURI:Uri, sdCardURI:Uri?):ServiceResult{

        val fileMetaMap=mutableMapOf<String, FileMeta>()
        val filesInFileSystem= mutableListOf<FileMeta>()
        val filesToRepair= mutableListOf<FileMeta>()
        filesInFileSystem.addAll(fileHandlerHelper.getAllFilesMetaInDirectory(internalURI))
        if(sdCardURI!=null){
            filesInFileSystem.addAll(fileHandlerHelper.getAllFilesMetaInDirectory(sdCardURI))
        }

        if(filesInFileSystem.isEmpty()){
            return ServiceResult(true,"No files found in the file system")
        }

        val filesInDb= fileDao.getAllFiles()
        for(file in filesInDb){
            fileMetaMap[file.fileName]=file
        }

        for(file in filesInFileSystem) {
            val fileName=file.fileName
            val fileInDb=fileMetaMap[fileName]
            if(fileInDb!=null && fileInDb!=file){
                //found the file in DB with same filename but different uri
                fileInDb.fileUri=file.fileUri
                filesToRepair.add(fileInDb)
            }
        }

        var updateCount=0

        if(filesToRepair.isNotEmpty()) {
            for(file in filesToRepair){
                fileDao.updateFileUri(file.fileId,file.fileUri.toString())
                updateCount++
            }
            return ServiceResult(success = true, message = "Repaired $updateCount files")
        }else{
            return ServiceResult(success = true, message = "No files to repair")
        }

    }

    fun syncFileSizesWithStorage(){
        val files=fileDao.getAllFiles()
        for(file in files){
            if(file.fileSize==0L){
                val path=file.fileUri.path
                if(path!==null){
                    val fileObject=File(path)
                    if(fileObject.exists()){
                        val size=fileObject.length()
                        fileDao.updateFileSize(file.fileId,size)
                    }
                }
            }
        }
    }

    fun populateMissingDurations(){
        val filesWithoutDuration=fileDao.getFilesMissingDurationSimple()
        for(fileData in filesWithoutDuration){
            val path=fileData.fileUri.path
            if(path==null){
                continue
            }
            val file=File(path)
            if(file.exists()){
                val duration=getMediaDuration(file)
                fileDao.updateFileDuration(fileData.fileId,duration)
            }
        }
    }
    fun getFileDetails(fileId: Long?): ServiceResult {
        if(fileId==null){
            return ServiceResult(success = false, message = "Missing ID")
        }

        val result=fileDao.getFileWithPerformers(fileId)
        if(result.isEmpty()){
            return ServiceResult(success = false, message = "No details found")
        }

        val name=result.first().fileName
        val id=result.first().fileId
        val items:MutableList<Item> =  mutableListOf()

        for(item in result){
            if(item.performerId!=null && item.performerName!=null){
                items.add(Item(item.performerId,item.performerName))
            }
        }

        val fileDetails= FileDetails(name,id,items)
        val gson: Gson = GsonBuilder().create()
        val jsonContent: String = gson.toJson(fileDetails)
        return ServiceResult(success = true, message = jsonContent)

    }

    fun renameFile(fileId:Long ,newName: String):ServiceResult{
        if (!fileHandlerHelper.isValidFilename(newName)) {
            return ServiceResult(false,"Invalid filename provided")
        }
        val fileMeta = fileDao.getFileById(fileId) ?: return ServiceResult(false, "File with id $fileId not found")
        val oldName=fileMeta.fileName
        val dotIndex=oldName.lastIndexOf('.')
        val extension=if(dotIndex!=-1) oldName.substring(dotIndex) else ""
        val newNameWithExtension=newName+extension
        val path=fileMeta.fileUri.path
        if(path==null){
            return ServiceResult(false,"File not found in the file system")
        }
        val file = File(path)
        if (!file.exists()||!file.isFile) {
            return ServiceResult(false,"File not found in the file system")
        }
        val newFile=File(file.parentFile,newNameWithExtension)
        if(newFile.exists()){
            return ServiceResult(false,"File with the same name already exists")
        }
        val success=file.renameTo(newFile)
        if(!success) {
            return ServiceResult(false,"Could not rename file")
        }
        val updatedFileMeta = fileMeta.copy(fileName = newFile.name.toString(), fileUri =  Uri.fromFile(newFile))
        fileDao.updateFile(updatedFileMeta)

        return ServiceResult(true, "File renamed successfully")
    }
    fun streamFile(fileId: Long,headers: Map<String, String> ,downloadFlag:Boolean): NanoHTTPD.Response {

        val fileMeta = fileDao.getFileById(fileId)
        val filePath=fileMeta?.fileUri?.path
        val gson: Gson = GsonBuilder().create()
        val responseContent= mapOf("message" to "File not found or inaccessible")
        val fileNotFoundResponse = newFixedLengthResponse(
            Status.NOT_FOUND,
            "application/json",
            gson.toJson(responseContent)
        )
        if(filePath==null){
            return fileNotFoundResponse
        }

        val file = File(filePath)
        if (!file.exists()||!file.isFile) {
            return fileNotFoundResponse
        }
        try {
            val fileLength = file.length()
            val rangeHeader = headers["range"]

            return if (rangeHeader != null && rangeHeader.startsWith("bytes=") ) {
                getPartialResponse(file, rangeHeader, fileLength, downloadFlag)
            }else{
                getFullResponse(file,downloadFlag)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            val responseContent= mapOf("message" to "Error when streaming file")
            return newFixedLengthResponse(Status.NOT_FOUND, "application/json", gson.toJson(responseContent))
        }
    }


    private fun getFullResponse(file: File,downloadFlag: Boolean): NanoHTTPD.Response {
        try {
            val inputStream = FileInputStream(file)
            val fileSize = file.length()
            val response = NanoHTTPD.newChunkedResponse(
                Status.OK,
                mimeType,
                inputStream
            )
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Length", fileSize.toString())
            if (downloadFlag) {
                val dispositionValue = "attachment; filename=\"${file.name}\""
                response.addHeader("Content-Disposition", dispositionValue)
            }
            return response
        }catch (e: Exception) {
            // Handle exceptions or errors while accessing the file
            e.printStackTrace()
            return newFixedLengthResponse(
                Status.INTERNAL_ERROR,
                "text/plain",
                "Internal Server Error"
            )
        }
    }


    private fun getPartialResponse(file: File, rangeHeader: String, fileLength: Long, downloadFlag:Boolean): NanoHTTPD.Response {

        try {
            val fileInputStream = FileInputStream(file)

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
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Length", contentLength.toString())
            response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
            if (downloadFlag) {
                val dispositionValue = "attachment; filename=\"${file.name}\""
                response.addHeader("Content-Disposition", dispositionValue)
            }
            return response

        } catch (e: Exception) {
            e.printStackTrace()
            val gson: Gson = GsonBuilder().create()
            val responseContent = mapOf("message" to "Error while seeking for file")
            val jsonContent: String = gson.toJson(responseContent)
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json",jsonContent )
        }
    }

    fun getMediaDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            return time?.toLong() ?: 0L
        } catch (_: Exception) {
            return 0L
        } finally {
            retriever.release()
        }
    }

    fun getChunkedUploadStatus(fileName: String, fileSize: Long, chunkSize: Long, target: String): JSONObject {
        val normalizedTarget = target.lowercase()
        val existingFile = fileDao.isFileNamePresent(fileName)
        if (existingFile) {
            Log.d("FileService", "getChunkedUploadStatus: FILE_NAME_CONFLICT for $fileName")
            val response = JSONObject()
            response.put("fileExists", true)
            response.put("error", "FILE_NAME_CONFLICT")
            response.put("message", "A file with the same name already exists.")
            response.put("fileName", fileName)
            response.put("status", "COMPLETED")
            response.put("action", "RENAME_REQUIRED")
            return response
        }

        val progress = uploadProgressDao.getByFileNameAndTarget(fileName, normalizedTarget)
        if (progress != null) {
            if (progress.fileSize != fileSize) {
                Log.e("FileService", "getChunkedUploadStatus: FILE_SIZE_MISMATCH for $fileName. Expected $fileSize, found ${progress.fileSize}")
                val response = JSONObject()
                response.put("fileExists", true)
                response.put("error", "FILE_SIZE_MISMATCH")
                response.put("message", "A file with the same name but different size exists in upload queue.")
                response.put("fileName", fileName)
                response.put("status", "CONFLICT")
                response.put("action", "RENAME_REQUIRED")
                return response
            }

            val response = JSONObject()
            response.put("fileExists", true)
            response.put("chunkSize", progress.chunkSize)
            response.put("totalChunks", progress.totalChunks)
            response.put("uploadedChunks", progress.uploadedChunks)
            response.put("nextChunkIndex", progress.uploadedChunks)
            response.put("status", progress.status)
            return response
        } else {
            val rootUri = if (normalizedTarget == "internal") {
                prefHandler.getInternalURI()
            } else {
                prefHandler.getSDCardURI()
            } ?: run {
                return JSONObject().put("error", "STORAGE_NOT_CONFIGURED")
            }

            val rootPath = rootUri.path ?: run {
                return JSONObject().put("error", "PATH_NOT_FOUND")
            }
            val destFile = File(rootPath, fileName)
            val fileUri = Uri.fromFile(destFile)

            val totalChunks = ceil(fileSize.toDouble() / chunkSize).toInt()
            val newProgress = UploadProgress(
                fileName = fileName,
                fileUri = fileUri,
                fileSize = fileSize,
                chunkSize = chunkSize,
                totalChunks = totalChunks,
                uploadedChunks = 0,
                target = normalizedTarget,
                status = "NEW"
            )
            uploadProgressDao.insert(newProgress)

            val response = JSONObject()
            response.put("fileExists", false)
            response.put("chunkSize", chunkSize)
            response.put("totalChunks", totalChunks)
            response.put("uploadedChunks", 0)
            response.put("nextChunkIndex", 0)
            response.put("status", "NEW")
            return response
        }
    }

    fun handleChunkUpload( chunkIndex: Int, fileName: String, fileSize: Long, target: String, chunkData: ByteArray): String {
        val normalizedTarget = target.lowercase()
        val progress = uploadProgressDao.getByFileNameAndTarget(fileName, normalizedTarget)
            ?: run {
                throw Exception("Upload progress not found for filename: $fileName in target: $normalizedTarget")
            }

        val filePath = progress.fileUri.path ?: run {
            throw Exception("File path not found in progress")
        }
        val destFile = File(filePath)

        val raf = java.io.RandomAccessFile(destFile, "rw")
        raf.seek(chunkIndex.toLong() * progress.chunkSize)
        raf.write(chunkData)
        raf.close()

        val newUploadedChunks = chunkIndex + 1
        progress.uploadedChunks = newUploadedChunks
        progress.updatedAt = System.currentTimeMillis()

        if (newUploadedChunks >= progress.totalChunks) {
            progress.status = "COMPLETED"
            uploadProgressDao.deleteByFileNameAndTarget(fileName, normalizedTarget)

            val uri = Uri.fromFile(destFile)
            val durationMs = getMediaDuration(destFile)
            val fileMeta = FileMeta(
                fileName = fileName,
                fileUri = uri,
                fileSize = fileSize,
                durationMs = durationMs
            )
            fileDao.insertFile(fileMeta)

            val response = JSONObject()
            response.put("uploadedChunks", progress.totalChunks)
            response.put("totalChunks", progress.totalChunks)
            response.put("remainingChunks", 0)
            response.put("percentComplete", 100)
            response.put("status", "COMPLETED")
            return response.toString()
        } else {
            progress.status = "IN_PROGRESS"
            uploadProgressDao.update(progress)

            val response = JSONObject()
            response.put("chunkIndex", chunkIndex)
            response.put("uploadedChunks", newUploadedChunks)
            response.put("totalChunks", progress.totalChunks)
            response.put("remainingChunks", progress.totalChunks - newUploadedChunks)
            response.put("percentComplete", (newUploadedChunks.toDouble() / progress.totalChunks * 100).toInt())
            response.put("status", "IN_PROGRESS")
            return response.toString()
        }
    }
}