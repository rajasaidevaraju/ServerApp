import helpers.FileHandlerHelper
import helpers.SharedPreferencesHelper
import fi.iki.elonen.NanoHTTPD
import android.content.Context
import android.util.Log
import database.AppDatabase
import server.service.FileService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.json.JSONObject
import server.service.DBService
import server.service.NetworkService


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
        var response: Response=newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        val sdCardURI = prefHandler.getSDCardURI()
        val internalURI = prefHandler.getInternalURI()
        val gson: Gson = GsonBuilder().create()

        if(sdCardURI==null && internalURI==null){
            response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No Root Folder Selected")
            return response
        }
        when(url){

            "/status"->{
                val responseContent = mapOf("alive" to true)
                val jsonContent: String = gson.toJson(responseContent)
                response = newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonContent)
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
                response = newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonContent)
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
                        response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON,jsonContent )
                    }
                }
                else{
                    val responseContent = mapOf("message" to "Improper url")
                    val jsonContent: String = gson.toJson(responseContent)
                    response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, jsonContent)
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
                response= newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonContent)
            }
            "/clean"->{
                val removedEntries=dbService.removeAbsentEntries(context,database.fileDao())
                val responseContent = mapOf("rows_deleted" to removedEntries)
                val jsonContent: String = gson.toJson(responseContent)
                response= newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonContent)
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
                    response= newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonContent)

                }catch (error:Exception){
                    val responseContent = mapOf("message" to "screenshot insert or update operation failed")
                    val jsonContent: String = gson.toJson(responseContent)
                    Log.d("fileId error",error.toString())
                    response=newFixedLengthResponse(Response.Status.INTERNAL_ERROR,MIME_JSON,jsonContent)

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
                        val jsonContent: String = gson.toJson(responseContent)
                        response= newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonContent)
                    } catch (e: NumberFormatException) {
                        val responseContent = mapOf("message" to "Invalid File ID")
                        val jsonContent: String = gson.toJson(responseContent)
                        response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, jsonContent)
                    }
                }else{
                    val responseContent = mapOf("message" to "Improper url")
                    val jsonContent: String = gson.toJson(responseContent)
                    response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, jsonContent)
                }
            }
            "/upload"->{
                if(prefHandler.getInternalURI()==null){
                    val responseContent = mapOf(
                        "message" to "Server configuration error: File path not configured.",
                        "error" to "File upload failed"
                    )
                    val jsonContent: String = gson.toJson(responseContent)
                    response= newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, jsonContent)
                }else{
                    //write code here
                }
            }
        }
        return response
    }



    override fun serve(session: IHTTPSession): Response {

        val uiServerLocation=prefHandler.getFrontEndUrl()
        val uiServerMode=prefHandler.getUIServerMode()
        val gson: Gson = GsonBuilder().create()
        var response: Response=newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, gson.toJson(mapOf("message" to "The requested resource could not be found")))
        var url= session.uri ?: return response

        if(url.startsWith("/server")){
            url=url.replace("/server","")
            response=handleServerRequest(url,session)
        }
        else{

            if(uiServerMode==false){
                response=staticBuiltUI(url)
            }else{
                if(uiServerLocation==null){
                    response=newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, gson.toJson(mapOf("message" to "UI Server not found")))

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
            Response.Status.NOT_FOUND,
            "text/plain",
            "File not found"
        )
    }

    private fun addCorsHeaders(response: Response,backEndUrl:String) {
        response.addHeader("Access-Control-Allow-Origin", backEndUrl)
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
    }
}

