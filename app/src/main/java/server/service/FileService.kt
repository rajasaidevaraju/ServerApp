package server.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageVolume
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import database.AppDatabase
import database.dao.FileDetails
import database.dao.Item
import database.entity.FileMeta
import database.jointable.VideoActressCrossRef
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import helpers.FileHandlerHelper
import helpers.SharedPreferencesHelper
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream


class FileService(private val database: AppDatabase,private val fileHandlerHelper: FileHandlerHelper, private val prefHandler: SharedPreferencesHelper, private val context: Context) {

    private val mimeType = "video/mp4"
    private val fileDao=database.fileDao()

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
                newFiles.add(fileMeta)
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

    fun renameFile(fileId:Long ,newName: String, context: Context):ServiceResult{
        if (!fileHandlerHelper.isValidFilename(newName)) {
            return ServiceResult(false,"Invalid filename provided")
        }
        val fileMeta = fileDao.getFileById(fileId)
        if(fileMeta==null){
            return ServiceResult(false,"File with id $fileId not found")
        }
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
    fun streamFile(fileId: Long, context: Context, headers: Map<String, String>): NanoHTTPD.Response {

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
                getPartialResponse(file, rangeHeader, fileLength, context)
            }else{
                getFullResponse(file, context)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            val responseContent= mapOf("message" to "Error when streaming file")
            return newFixedLengthResponse(Status.NOT_FOUND, "application/json", gson.toJson(responseContent))
        }
    }


    private fun getFullResponse(file: File,context:Context): NanoHTTPD.Response {
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


    private fun getPartialResponse(file: File, rangeHeader: String, fileLength: Long, context:Context): NanoHTTPD.Response {

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