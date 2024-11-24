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
    private val fileSerivice by lazy { FileService() }
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
        val rootUri = prefHandler.getURI()
        val gson: Gson = GsonBuilder().create()

        if(rootUri==null){
            response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No Root Folder Selected")
            return response;
        }
        when(url){

            "/"->{
                response=newFixedLengthResponse("Hello from the server!")
            }

            "/files"->{
                val params = session.parameters
                val page = params["page"]?.firstOrNull()
                var pageNo=1
                val pageSize=12
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
                response = newFixedLengthResponse(Response.Status.OK, "application/json", jsonContent)
            }
            "/file"->{
                val params = session.parameters
                val fileIdStr = params["fileId"]?.firstOrNull()

                if (!fileIdStr.isNullOrBlank()) {
                    try {
                        val fileId=fileIdStr.toLong()
                        response=fileSerivice.streamFile(fileId,context,database,session.headers)
                        //addCorsHeaders(response)

                    } catch (e: NumberFormatException) {
                        response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Invalid File ID")
                    }
                }
                else{
                    response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Improper url")
                }
            }
            "/scan"->{
                response= fileSerivice.scanFolder(rootUri,fileHandlerHelper,database)
            }
            "/clean"->{
                val removedEntries=dbService.removeAbsentEntries(context,database.fileDao())
                response = newFixedLengthResponse(Response.Status.OK,
                    "text/plain",
                    "$removedEntries invalid rows deleted")
            }
            "/upload-screenshot"->{
                try {
                    val postBody=HashMap<String,String>()
                    session.parseBody(postBody) // Parse the body into a map
                    //val postBody = session.queryParameterString

                    val postData=postBody["postData"]
                    val insertedFileId=dbService.insertScreenshotData(postData, database.fileDao())
                    val jsonResponse = JSONObject()
                    jsonResponse.put("message", "Screenshot inserted or updated for file with ID $insertedFileId")
                    val jsonContent = jsonResponse.toString()
                    response=newFixedLengthResponse(Response.Status.OK,"application/json",jsonContent)

                }catch (error:Exception){
                    val jsonContent: String = gson.toJson("{message:'screenshot insert or update operation failed'}")
                    Log.d("fileId error",error.toString())
                    response=newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"application/json",jsonContent)

                }
            }
            "/thumbnail"->{
                val params = session.parameters
                val fileIdStr = params["fileId"]?.firstOrNull()

                if (!fileIdStr.isNullOrBlank()) {
                    try {
                        val fileId=fileIdStr.toLong()
                        var thumbnail=database.fileDao().getScreenshotData(fileId);
                        var exists=true;
                        if(thumbnail==null){
                            thumbnail=""
                            exists=false
                        }
                        val jsonResponse = JSONObject()
                        jsonResponse.put("imageData", thumbnail)
                        jsonResponse.put("exists", exists)
                        val jsonContent = jsonResponse.toString()
                        response=newFixedLengthResponse(Response.Status.OK,"application/json",jsonContent)
                    } catch (e: NumberFormatException) {
                        response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Invalid File ID")
                    }
                }else{
                    response= newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Improper url")
                }
            }
        }
        return response
    }



    override fun serve(session: IHTTPSession): Response {

        var response: Response=newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        var url= session.uri ?: return response;
        val uiServerLocation=prefHandler.getFrontEndUrl()
        val uiServerMode=prefHandler.getUIServerMode()
        if(url.startsWith("/server")){
            url=url.replace("/server","")
            response=handleServerRequest(url,session)
        }
        else{

            if(uiServerMode==false){
                response=staticBuiltUI(url)
            }else{
                if(uiServerLocation==null){
                    response= newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "UI Server not found")
                }else{
                    response=networkService.proxyRequestToUiServer(uiServerLocation,url,session)
                }
            }

        }
        val backEndUrl=prefHandler.getBackEndUrl()
        if(!backEndUrl.isNullOrBlank()){
            addCorsHeaders(response,backEndUrl)
        }
        return response;
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

