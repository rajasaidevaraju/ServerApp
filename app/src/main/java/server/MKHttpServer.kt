import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.core.content.ContextCompat.getExternalFilesDirs
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import database.AppDatabase
import database.entity.FileMeta
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import helpers.FileHandlerHelper
import helpers.SharedPreferencesHelper
import server.service.DBService
import server.service.FileService
import server.service.NetworkService
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID


enum class ExtendedStatus(val requestStatusExt: Int, val descriptionExt: String) :
    NanoHTTPD.Response.IStatus {
    INSUFFICIENT_STORAGE(507 , "Insufficient Storage") {
        override fun getDescription(): String {
            return "" + this.requestStatusExt + " " + this.descriptionExt
        }

        override fun getRequestStatus(): Int {
            return this.requestStatusExt
        }
    };

    companion object {
        fun lookup(requestStatus: Int): ExtendedStatus? {
            return entries.find { it.requestStatusExt == requestStatus }
        }
    }

    override fun toString(): String {
        return "$requestStatusExt $descriptionExt"
    }
}

class MKHttpServer(private val context: Context) : NanoHTTPD(1280) {
    private var isRunning = false
    private val prefHandler by lazy { SharedPreferencesHelper(context) }
    private val fileHandlerHelper by lazy { FileHandlerHelper(context) }
    private val networkService by lazy { NetworkService() }
    private val fileService by lazy { FileService() }
    private val MIME_JSON="application/json"
    private val dbService by lazy { DBService() }
    private val database  by lazy { AppDatabase.getDatabase(context) }

    override fun start() {

        if (!isRunning) {
            super.start()
            isRunning = true
        }
    }

    override fun stop() {
        if (isRunning) {
            super.stop()
            isRunning = false
        }
    }

    private fun handleServerRequest(url: String, session: IHTTPSession): Response{
        var response: Response=newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        val sdCardURI = prefHandler.getSDCardURI()
        val internalURI = prefHandler.getInternalURI()
        val gson: Gson = GsonBuilder().create()

        if(sdCardURI==null && internalURI==null){
            val responseContent = mapOf("message" to "No Root Folder Selected")
            val jsonContent: String = gson.toJson(responseContent)
            response = newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, jsonContent)
            return response
        }
        when(url){

            "/status"->{
                val responseContent = mapOf("alive" to true)
                val jsonContent: String = gson.toJson(responseContent)
                response = newFixedLengthResponse(Status.OK, MIME_JSON, jsonContent)
            }

            "/files"->{
                val params = session.parameters
                val page = params["page"]?.firstOrNull()
                var pageNo=1
                val pageSize=20
                if(!page.isNullOrBlank()){
                    pageNo=page.toIntOrNull()?:1
                }
                val offSet=(pageNo-1)*pageSize
                val paginatedFileData = database.fileDao().getSimplifiedFilesMetaPagenated(offSet,pageSize)
                val totalFiles = database.fileDao().getTotalFileCount()
                val responseContent = mapOf(
                    "data" to paginatedFileData,
                    "meta" to mapOf(
                        "page" to pageNo,
                        "limit" to pageSize,
                        "total" to totalFiles
                    )
                )
                val jsonContent: String = gson.toJson(responseContent)
                response = newFixedLengthResponse(Status.OK, MIME_JSON, jsonContent)
            }
            "/file"->{
                val params = session.parameters
                val fileIdStr = params["fileId"]?.firstOrNull()

                if (!fileIdStr.isNullOrBlank()) {
                    try {
                        val fileId=fileIdStr.toLong()
                        response=fileService.streamFile(fileId,context,database,session.headers)
                        //addCorsHeaders(response)

                    } catch (e: NumberFormatException) {
                        val responseContent = mapOf("message" to "Invalid File ID")
                        val jsonContent: String = gson.toJson(responseContent)
                        response= newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON,jsonContent )
                    }
                }
                else{
                    val responseContent = mapOf("message" to "Improper url")
                    val jsonContent: String = gson.toJson(responseContent)
                    response= newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, jsonContent)
                }
            }
            "/scan"->{
                val rows:MutableList<Long> = mutableListOf()
                if(sdCardURI!=null){
                    val tempRows=fileService.scanFolder(sdCardURI,fileHandlerHelper,database)
                    rows.addAll(tempRows)
                }
                if(internalURI!=null){
                    val tempRows=fileService.scanFolder(internalURI,fileHandlerHelper,database)
                    rows.addAll(tempRows)
                }
                val notInsertedRows = rows.count {it==-1L}
                val responseContent = mapOf(
                    "insertions_attempted" to rows.size,
                    "not_inserted" to notInsertedRows,
                    "inserted" to rows.size-notInsertedRows
                    )
                val jsonContent: String = gson.toJson(responseContent)
                response= newFixedLengthResponse(Status.OK, MIME_JSON, jsonContent)
            }
            "/clean"->{
                val removedEntries=dbService.removeAbsentEntries(context,database.fileDao())
                val responseContent = mapOf("rows_deleted" to removedEntries)
                val jsonContent: String = gson.toJson(responseContent)
                response= newFixedLengthResponse(Status.OK, MIME_JSON, jsonContent)
            }
            "/upload-screenshot"->{
                try {
                    val postBody=HashMap<String,String>()
                    session.parseBody(postBody) // Parse the body into a map


                    val postData=postBody["postData"]
                    val insertedFileId=dbService.insertScreenshotData(postData, database.fileDao())
                    val responseContent = mapOf(
                        "message" to "Screenshot inserted or updated for file with ID $insertedFileId",
                        "fileId" to insertedFileId)
                    val jsonContent: String = gson.toJson(responseContent)
                    response= newFixedLengthResponse(Status.OK, MIME_JSON, jsonContent)

                }catch (error:Exception){
                    val responseContent = mapOf("message" to "screenshot insert or update operation failed")
                    val jsonContent: String = gson.toJson(responseContent)
                    Log.d("fileId error",error.toString())
                    response=newFixedLengthResponse(Status.INTERNAL_ERROR,MIME_JSON,jsonContent)

                }
            }
            "/thumbnail"->{
                val params = session.parameters
                val fileIdStr = params["fileId"]?.firstOrNull()

                if (!fileIdStr.isNullOrBlank()) {
                    try {
                        val fileId=fileIdStr.toLong()
                        var thumbnail=database.fileDao().getScreenshotData(fileId)
                        var exists=true
                        if(thumbnail==null){
                            thumbnail=""
                            exists=false
                        }
                        val responseContent = mapOf(
                            "imageData" to thumbnail,
                            "exists" to exists)
                        response= newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(responseContent))
                    } catch (e: NumberFormatException) {
                        val responseContent = mapOf("message" to "Invalid File ID")
                        response= newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson(responseContent))
                    }
                }else{
                    val responseContent = mapOf("message" to "Improper url")
                    val jsonContent: String = gson.toJson(responseContent)
                    response= newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, jsonContent)
                }
            }
            "/stats"->{
                try {
                    val hasExternalStorage = fileHandlerHelper.isSdCardAvailable()
                    val files = database.fileDao().getTotalFileCount()
                    var totalInternal:Long = 0
                    var freeInternal:Long = 0
                    var totalExternal: Long = 0
                    var freeExternal: Long = 0

                    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager?
                    if(storageStatsManager!=null){
                        val  storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager;
                        val storageVolumes = storageManager.getStorageVolumes();
                        for ( storageVolume: StorageVolume in storageVolumes) {
                            val uuid = storageVolume.storageUuid
                            Log.d("uuid:", uuid.toString())
                             Log.d("storage stats isRemovable",  storageVolume.isRemovable.toString())
                            Log.d("storage stats description",storageVolume.getDescription(context))
                            if(uuid!=null){
                                Log.d("storage stats TotalBytes", storageStatsManager.getTotalBytes(uuid).toString())
                                Log.d("storage stats FreeBytes",  storageStatsManager.getFreeBytes(uuid).toString())

                                if(storageVolume.isPrimary){
                                    totalInternal=storageStatsManager.getTotalBytes(uuid)
                                    freeInternal=storageStatsManager.getFreeBytes(uuid)
                                }else{
                                    totalExternal=storageStatsManager.getTotalBytes(uuid)
                                    freeExternal=storageStatsManager.getFreeBytes(uuid)
                                }
                            }else{
                               val path=storageVolume.directory
                                if(path!=null && !storageVolume.isPrimary){
                                    val statFs=StatFs(path.absolutePath)
                                    freeExternal=statFs.availableBytes
                                    totalExternal=statFs.totalBytes
                                }else{
                                    Log.d("Fuck Google","Fuck Google")
                                }
                            }
                        }
                    }

                    val responseContent = mapOf(
                        "files" to files,
                        "freeInternal" to freeInternal,
                        "totalInternal" to totalInternal,
                        "freeExternal" to freeExternal,
                        "totalExternal" to totalExternal,
                        "hasExternalStorage" to hasExternalStorage
                    )
                    response = newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(responseContent))
                }catch (exception:Exception){
                    val responseContent =  mapOf("message" to "Stats retrieval failed", "error" to getExceptionString(exception))
                    response = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                }
            }
            "/upload"->{

                if (internalURI != null) {
                    val destinationDir= DocumentFile.fromTreeUri(context,internalURI)
                    if(destinationDir!=null){
                        try{
                            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                                .withZone(ZoneId.of("UTC"))
                            val contentLength = session.headers["content-length"]!!
                                .toLong()
                            val threeGBInBytes = 3L * 1024 * 1024 * 1024
                            if(fileService.getFreeInternalMemorySize()-contentLength<threeGBInBytes){
                                response = newFixedLengthResponse(ExtendedStatus.INSUFFICIENT_STORAGE, MIME_JSON, gson.toJson(mapOf("message" to "Not enough storage.")))
                            }else{

                                val boundary = extractBoundary(session.headers)
                                if(boundary==null){
                                    val responseContent = mapOf("message" to "No Boundary in headers")
                                    return newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(responseContent))
                                }else{
                                    val filename="defaultFIleName${formatter.format(Instant.now())}.mp4"
                                    val destinationFile=destinationDir.createFile("video/mp4", filename)
                                    val destinationOutputStream = destinationFile?.uri?.let { context.contentResolver.openOutputStream(it) }
                                    if(destinationOutputStream!=null){
                                        val returnFilename=networkService.processMultipartFormData(session.inputStream,boundary,destinationOutputStream,contentLength)
                                        var responseContent = mapOf("message" to "File Stored Successfully")
                                        var exists=false;
                                        for (file in destinationDir.listFiles()) {
                                            if (file.name == returnFilename) {
                                                responseContent=mapOf("message" to "File Stored Successfully, But File Name Already Exists ")
                                                exists=true
                                            }
                                        }
                                        val fileMeta:FileMeta
                                        if(!exists){
                                            val success=destinationFile.renameTo(returnFilename)
                                            if(success){
                                                fileMeta=FileMeta(fileName=returnFilename, fileUri = destinationFile.uri)
                                            }else{
                                                fileMeta=FileMeta(fileName=filename, fileUri = destinationFile.uri)
                                            }
                                        }else{
                                            fileMeta=FileMeta(fileName=filename, fileUri = destinationFile.uri)
                                        }
                                        database.fileDao().insertFile(fileMeta)
                                        response = newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(responseContent))

                                    }else{
                                        val responseContent = mapOf("message" to "File creation failed")
                                        response = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                                        return response
                                    }

                                }

                            }
                        }catch(exception:Exception){
                            Log.d("exception",getExceptionString(exception))
                            val responseContent=mapOf(
                                "message" to "Could not complete file upload",
                                "error" to getExceptionString(exception)
                            )
                            response = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                        }
                    }else{
                        val responseContent = mapOf("message" to "Server configuration error: Save path not configured.")
                        response = newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(responseContent))
                    }

                } else {
                    val responseContent = mapOf("message" to "Server configuration error: Save path not configured.")
                    response = newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(responseContent))
                }
            }
        }
        return response
    }



    override fun serve(session: IHTTPSession): Response {

        val uiServerLocation=prefHandler.getFrontEndUrl()
        val uiServerMode=prefHandler.getUIServerMode()
        val gson: Gson = GsonBuilder().create()
        var responseContent=mapOf("message" to "The requested resource could not be found")
        var response: Response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson(responseContent))
        var url= session.uri ?: return response

        if(url.startsWith("/server")){
            url=url.replace("/server","")
            response=handleServerRequest(url,session)
        }
        else{

            if(!uiServerMode){
                response=staticBuiltUI(url)
            }else{
                if(uiServerLocation==null){
                    responseContent=mapOf("message" to "UI Server not found")
                    response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson(responseContent))

                }else{
                    response=networkService.proxyRequestToUiServer(uiServerLocation,url,session)
                }
            }

        }
        val backEndUrl=prefHandler.getBackEndUrl()
        if(!backEndUrl.isNullOrBlank()){
            addCorsHeaders(response,backEndUrl)
        }
        return response
    }

    private fun staticBuiltUI(url: String): Response{
        var filePath = url.removePrefix("/") // Remove leading slash
        if(filePath.isEmpty()){
            filePath=filePath.plus("index")
        }
        if(!filePath.contains(".")){
            filePath=filePath.plus(".html")
        }
        // Android excludes folders with underscores (_) in assets during API creation.
        // Since Next.js doesn't support changing the default _next folder name,
        // we rename the folder to "next" after build and reroute requests accordingly.
        if(filePath.startsWith("_next")){
            filePath=filePath.replaceFirst("_next","next")
        }
        return fileHandlerHelper.serveStaticFile(filePath) ?: newFixedLengthResponse(
            Status.NOT_FOUND,
            "text/plain",
            "File not found"
        )
    }

    private fun addCorsHeaders(response: Response,backEndUrl:String) {
        response.addHeader("Access-Control-Allow-Origin", backEndUrl)
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
    }

    private fun getExceptionString(exception:Exception):String{
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        exception.printStackTrace(printWriter)
        return stringWriter.toString()
    }
    private fun extractBoundary(headers:Map<String, String>):String?{
        val contentType=headers.get("content-type")
        // example of contentType multipart/form-data; boundary=----WebKitFormBoundaryuRqy8BtHu1nT2dfA
        val regex = "boundary=(.+)".toRegex()
        return contentType?.let { regex.find(it)?.groups?.get(1)?.value }
    }
}

