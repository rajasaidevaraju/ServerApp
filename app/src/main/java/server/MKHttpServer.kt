import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import database.AppDatabase
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import helpers.FileHandlerHelper
import helpers.SharedPreferencesHelper
import server.service.DBService
import server.service.FileService
import server.service.NetworkService
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


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
            response= newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "No Root Folder Selected")
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
                                        networkService.processMultipartFormData(session.inputStream,boundary,destinationOutputStream,contentLength)
                                        val responseContent = mapOf("message" to "File Stored Successfully")
                                        response = newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(responseContent))
                                    }else{
                                        val responseContent = mapOf("message" to "File creation failed")
                                        response = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                                        return response
                                    }

                                }

                                /*
                                val files: Map<String, String> = HashMap()
                                session.parseBody(files)
                                val keys: Set<String> = files.keys
                                for (key in keys) {
                                    val location = files[key]
                                    Log.d("FileUpload", "Key: $key, File Path: $location")
                                    val filename="defaultFIleName${formatter.format(Instant.now())}.mp4"
                                    val destinationFile=destinationDir.createFile("video/mp4", filename)
                                    if(destinationFile!=null && location !=null){
                                        val destinationOutputStream =context.contentResolver.openOutputStream(destinationFile.uri)
                                        val tempFile = File(location)
                                        val tempFIleInputStream=FileInputStream(tempFile)
                                        tempFIleInputStream.use { input ->
                                            destinationOutputStream.use { output ->
                                                if (output != null) {
                                                    input.copyTo(output)
                                                }else{
                                                    val responseContent = mapOf("message" to "Could not get output stream")
                                                    response = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                                                    return response
                                                }
                                            }
                                        }
                                    }else{
                                        val responseContent = mapOf("message" to "File creation failed")
                                        response = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                                        return response
                                    }
                                }*/
                            }
                        }catch(exception:Exception){
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

