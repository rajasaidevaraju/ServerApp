import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import database.AppDatabase
import database.entity.FileMeta
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import helpers.FileHandlerHelper
import helpers.NetworkHelper
import helpers.SharedPreferencesHelper
import server.service.DBService
import server.service.FileService
import server.service.UserService
import server.service.NetworkService
import java.io.PrintWriter
import java.io.StringWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.fixedRateTimer


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
    private val database  by lazy { AppDatabase.getDatabase(context) }
    private var isRunning = AtomicBoolean(false)
    private val prefHandler by lazy { SharedPreferencesHelper(context) }
    private val fileHandlerHelper by lazy { FileHandlerHelper(context) }
    private val networkHandler by lazy { NetworkHelper() }
    private val networkService by lazy { NetworkService() }
    private val userService by lazy {UserService(database)}
    private val fileService by lazy { FileService() }
    private val MIME_JSON="application/json"
    private val activeServers = ConcurrentHashMap<String,Instant>()
    private val broadcastPort = 6789
    private val broadcastInterval = 5000L
    private var broadcastThread: Thread? = null
    private var listeningThread: Thread? = null
    private val dbService by lazy { DBService() }


    override fun start() {
        isRunning.set(super.isAlive())
        if (!isRunning.get()) {
            super.start()
            startBroadcasting()
            startListeningForBroadcasts()
        }
    }

    override fun stop() {
        isRunning.set(super.isAlive())
        if (isRunning.get()) {
            super.stop()
            stopBroadcasting()
            stopListeningForBroadcasts()

        }
    }

    private fun startBroadcasting() {

        broadcastThread = Thread {
            fixedRateTimer(period = broadcastInterval) {
                val format: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US)
                val timeStamp=format.format(Date())
                var message ="$timeStamp: Server active at: ${InetAddress.getLocalHost().hostAddress} "
                var ipAddress=networkHandler.getIpAddress(context)
                if(ipAddress!=null){
                    if(ipAddress == "null"){
                        ipAddress="localhost"
                    }
                    message ="$timeStamp: Server active at: $ipAddress"
                }
                val socket = DatagramSocket()
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val buffer = message.toByteArray()
                if (!isRunning.get()) cancel()
                val packet = DatagramPacket(buffer, buffer.size, broadcastAddress, broadcastPort)
                socket.send(packet)
            }
        }.apply { start() }
    }

    private fun stopBroadcasting() {
        broadcastThread?.interrupt()
        broadcastThread = null
    }

    private fun startListeningForBroadcasts() {
        listeningThread = Thread {
            val socket = DatagramSocket(broadcastPort)
            val buffer = ByteArray(1024)

            while (isRunning.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    val senderIP = packet.address.hostAddress
                    var deviceIP=networkHandler.getIpAddress(context)
                    if(deviceIP!=null && senderIP!=null) {
                        if (deviceIP == "null") {
                            deviceIP = "localhost"
                        }
                        if (message.contains("Server active at:") && senderIP!=deviceIP) {
                            activeServers[senderIP] = Instant.now()
                        }
                    }
                } catch (e: Exception) {
                    if (!isRunning.get()) break
                }
            }
            socket.close()
        }.apply { start() }
    }

    private fun stopListeningForBroadcasts() {
        listeningThread?.interrupt()
        listeningThread = null
    }



    private fun handleGetRequest(url: String, session: IHTTPSession): Response{
        val gson: Gson = GsonBuilder().create()
        var response: Response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson( mapOf("message" to "The requested resource could not be found")))

        when(url){
            "/status"->{
                val responseContent = mapOf("alive" to true)
                val jsonContent: String = gson.toJson(responseContent)
                response = newFixedLengthResponse(Status.OK, MIME_JSON, jsonContent)
            }
            "/name"->{
                val params = session.parameters

                val fileIdStr = params["fileId"]?.firstOrNull()

                if (fileIdStr.isNullOrBlank()) {
                    val responseContent = mapOf("message" to "Missing required parameter: fileId")
                    return  newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(responseContent))
                }
                try{
                    val fileId=fileIdStr.toLong()
                    val fileMeta=database.fileDao().getFileById(fileId)
                    if(fileMeta==null){
                        return newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(mapOf("message" to "file ID $fileId not present")))
                    }
                    return newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(mapOf("fileName" to fileMeta.fileName)))

                }catch (exception: NumberFormatException){
                    return newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(mapOf("message" to "Invalid fileId")))
                }
                catch (exception:Exception){
                    val responseContent =  mapOf("message" to "Name retrieval failed for fileId: $fileIdStr", "error" to getExceptionString(exception))
                    return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                }

            }

            "/files"->{
                val params = session.parameters
                val page = params["page"]?.firstOrNull()
                var pageNo=1
                val pageSize=18
                if(!page.isNullOrBlank()){
                    pageNo=page.toIntOrNull()?:1
                }
                val offSet=(pageNo-1)*pageSize
                val paginatedFileData = database.fileDao().getSimplifiedFilesMetaPagenated(offSet,pageSize)
                val totalFiles = database.fileDao().getTotalFileCount()
                if(paginatedFileData.isEmpty()){
                    return newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson(mapOf("message" to "No files found")))
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
                response = newFixedLengthResponse(Status.OK, MIME_JSON, jsonContent)
            }
            "/file"->{
                val params = session.parameters
                val fileIdStr = params["fileId"]?.firstOrNull()

                if (!fileIdStr.isNullOrBlank()) {
                    try {
                        val fileId=fileIdStr.toLong()
                        response=fileService.streamFile(fileId,context,database,session.headers)

                    } catch (e: NumberFormatException) {
                        val responseContent = mapOf("message" to "Invalid File ID")
                        val jsonContent: String = gson.toJson(responseContent)
                        response= newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON,jsonContent )
                    }
                }
                else{
                    val responseContent = mapOf("message" to "Missing required parameter: fileId")
                    response= newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(responseContent))
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
                        val responseContent = mapOf("message" to "Could not get thumbnail for id: $fileIdStr")
                        response= newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                    }
                }else{
                    val responseContent = mapOf("message" to "Missing required parameter: fileId")
                    response= newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(responseContent))
                }
            }
            "/servers"->{
                val oneMinuteAgo = Instant.now().minusSeconds(60)
                val list=ArrayList<String>()
                for(server in activeServers.entries){
                    val instant=server.value
                    val ipAddress=server.key
                    Log.d("ServerInfo", "Instant: $instant, IP Address: $ipAddress")

                    if(instant.isAfter(oneMinuteAgo)){
                        list.add(ipAddress)
                    }

                }
                val responseContent = mapOf("activeServers" to list)
                return newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(responseContent))
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

                    // battery percentage and charging status
                    val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

                    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    val batteryPct = if (level != -1 && scale != -1) {
                        (level / scale.toFloat() * 100).toInt()
                    } else {
                        -1
                    }

                    val responseContent = mapOf(
                        "files" to files,
                        "freeInternal" to freeInternal,
                        "totalInternal" to totalInternal,
                        "freeExternal" to freeExternal,
                        "totalExternal" to totalExternal,
                        "hasExternalStorage" to hasExternalStorage,
                        "percentage" to batteryPct,
                        "charging" to isCharging
                    )
                    response = newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(responseContent))
                }catch (exception:Exception){
                    val responseContent =  mapOf("message" to "Stats retrieval failed", "error" to getExceptionString(exception))
                    response = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                }
            }
        }
        return response
    }

    private fun handlePostRequest(url: String, session: IHTTPSession,sdCardURI: Uri?, internalURI:Uri): Response{
        val gson: Gson = GsonBuilder().create()
        var response: Response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON,
            gson.toJson(mapOf("message" to "The requested resource could not be found")))

        when(url){
            "/file"->{
                val destinationDir= DocumentFile.fromTreeUri(context,internalURI)
                if(destinationDir==null || destinationDir.isFile){
                    return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(mapOf("message" to "Could not store the file")))
                }
                var destinationFile:DocumentFile?=null
                try{
                    val contentLength = session.headers["content-length"]!!.toLong()
                    val threeGBInBytes = 3L * 1024 * 1024 * 1024
                    if(fileService.getFreeInternalMemorySize()-contentLength<threeGBInBytes){
                        return newFixedLengthResponse(ExtendedStatus.INSUFFICIENT_STORAGE, MIME_JSON, gson.toJson(mapOf("message" to "Not enough storage.")))
                    }

                    val boundary = extractBoundary(session.headers)
                    if(boundary==null){
                        return newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(mapOf("message" to "No Boundary in headers")))
                    }
                    val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneId.of("UTC"))
                    val fileName="defaultFIleName${formatter.format(Instant.now())}.mp4"
                    destinationFile=destinationDir.createFile("video/mp4", fileName)
                    val destinationFileURI=destinationFile?.uri
                    if(destinationFile==null || destinationFileURI==null){
                        return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(mapOf("message" to "File creation failed")))
                    }
                    val destinationOutputStream=context.contentResolver.openOutputStream(destinationFileURI)
                    if(destinationOutputStream==null){
                        return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(mapOf("message" to "Could not write to file")))
                    }
                    var originalFileName=networkService.processMultipartFormData(session.inputStream,boundary,destinationOutputStream,contentLength,destinationDir)
                    //check if file already exists in file system
                    var renameOp=false
                    if(!database.fileDao().isFileNamePresent(originalFileName)){
                        renameOp=destinationFile.renameTo(originalFileName)
                    }
                    if(!renameOp){
                        var i=1

                        originalFileName=insertStringBeforeExtension(originalFileName," ($i)")
                        while(destinationDir.findFile(originalFileName)!=null||database.fileDao().isFileNamePresent(originalFileName)){
                            i+=1
                            originalFileName=insertStringBeforeExtension(fileName," ($i)")
                            if(i==10){
                                originalFileName=fileName
                                break
                            }
                        }
                        destinationFile.renameTo(originalFileName)
                    }

                    val fileMeta=FileMeta(fileName=originalFileName, fileUri = destinationFile.uri)
                    database.fileDao().insertFile(fileMeta)
                    val responseContent = mapOf("message" to "File Stored Successfully")
                    response = newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(responseContent))

                }catch(exception:Exception){
                    Log.d("exception",getExceptionString(exception))
                    destinationFile?.delete()
                    val responseContent=mapOf(
                        "message" to "Could not complete file upload",
                        "stackTrace" to getExceptionString(exception)
                    )
                    response = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(responseContent))
                }
            }
            "/login"->{
                val data=HashMap<String,String>()
                session.parseBody(data)
                val postBody= data["postData"] ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(mapOf("message" to "no post data found")))

                val jsonObject = JsonParser.parseString(postBody).asJsonObject

                val username= jsonObject.get("username").asString
                val password= jsonObject.get("password").asString


                val usernameResult = userService.checkUsername(username)
                val passwordResult = userService.checkPassword(password)


                if(usernameResult.first && passwordResult.first && username!=null && password!=null){
                   val loginResult=  userService.loginUser(username,password)
                    if (loginResult.first) {
                        return newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(mapOf("message" to "Login successful")))
                    } else {
                        return newFixedLengthResponse(Status.UNAUTHORIZED, MIME_JSON, gson.toJson(mapOf("message" to loginResult.second)))
                    }
                }else{
                    var message=""
                    if (!usernameResult.first) {
                        message += usernameResult.second+" "
                    }
                    if (!passwordResult.first) {
                        message += passwordResult.second+" "
                    }
                    return newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(mapOf("message" to message)))

                }

            }
            "/thumbnail"->{
                try {
                    val postBody=HashMap<String,String>()
                    session.parseBody(postBody) // Parse the body into a map
                    val postData=postBody["postData"]
                    val insertedFileId=dbService.insertScreenshotData(postData, database.fileDao())
                    val responseContent = mapOf(
                        "message" to "Thumbnail inserted or updated for file with ID $insertedFileId",
                        "fileId" to insertedFileId)
                    val jsonContent: String = gson.toJson(responseContent)
                    response= newFixedLengthResponse(Status.OK, MIME_JSON, jsonContent)

                }catch (exception:Exception){
                    val jsonContent: String = gson.toJson(mapOf("message" to "Thumbnail insert or update operation failed","stackTrace" to getExceptionString(exception)))
                    Log.e("exception",getExceptionString(exception))
                    response=newFixedLengthResponse(Status.INTERNAL_ERROR,MIME_JSON,jsonContent)

                }
            }
            "/scan"->{
                val rows:MutableList<Long> = mutableListOf()
                if(sdCardURI!=null){
                    val tempRows=fileService.scanFolder(sdCardURI,fileHandlerHelper,database)
                    rows.addAll(tempRows)
                }

                val tempRows=fileService.scanFolder(internalURI,fileHandlerHelper,database)
                rows.addAll(tempRows)

                val notInsertedRows = rows.count {it==-1L}
                val responseContent = mapOf(
                    "insertions_attempted" to rows.size,
                    "not_inserted" to notInsertedRows,
                    "inserted" to rows.size-notInsertedRows
                )
                response= newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(responseContent))
            }
        }
        return response
    }
    private fun handleDeleteRequest(url: String, session: IHTTPSession,sdCardURI: Uri?, internalURI:Uri): Response{
       val gson: Gson = GsonBuilder().create()
        var response: Response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON,
            gson.toJson(mapOf("message" to "The requested resource could not be found")))

        when(url){
            "/cleanup"->{
                val removedEntries=dbService.removeAbsentEntries(context,database.fileDao())
                val responseContent = mapOf("message" to "$removedEntries rows removed",
                    "rows_deleted" to removedEntries)

                val jsonContent: String = gson.toJson(responseContent)
                response= newFixedLengthResponse(Status.OK, MIME_JSON, jsonContent)
            }
            "/file"->{
                val params = session.parameters
                val fileIdStr = params["fileId"]?.firstOrNull()

                if (fileIdStr.isNullOrBlank()) {
                    val responseContent = mapOf("message" to "Missing required parameter: fileId")
                    return newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(responseContent))
                }
                try {
                    val fileId=fileIdStr.toLong()
                    val fileMeta=database.fileDao().getFileById(fileId)
                    val file = fileMeta?.let { DocumentFile.fromTreeUri(context, it.fileUri) }
                    if (fileMeta == null || file == null || !file.isFile) {
                        val responseContent= mapOf("message" to "File not found or inaccessible")
                        return newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson(responseContent))
                    }
                    val isDeleted=file.delete()
                    if(isDeleted){
                        database.fileDao().deleteFile(fileMeta)
                        val responseContent= mapOf("message" to "File deleted successfully")
                        return newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(responseContent))
                    }else{
                        val responseContent = mapOf("message" to "Could not delete file for id: $fileIdStr")
                        return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON,gson.toJson(responseContent) )
                    }
                } catch (exception: Exception) {
                    val responseContent = mapOf("message" to "Could not delete file for id: $fileIdStr","stackTrace" to getExceptionString(exception))
                    Log.e("exception",getExceptionString(exception))
                    return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON,gson.toJson(responseContent) )
                }
            }
        }
        return response
    }

    private fun handlePutRequest(url: String, session: IHTTPSession,sdCardURI: Uri?, internalURI:Uri): Response {
        val gson: Gson = GsonBuilder().create()
        var response: Response = newFixedLengthResponse(
            Status.NOT_FOUND, MIME_JSON,
            gson.toJson(mapOf("message" to "The requested resource could not be found"))
        )
        return response
    }

    override fun serve(session: IHTTPSession): Response {

        val uiServerLocation=prefHandler.getFrontEndUrl()
        val uiServerMode=prefHandler.getUIServerMode()
        val sdCardURI = prefHandler.getSDCardURI()
        val internalURI = prefHandler.getInternalURI()
        val gson: Gson = GsonBuilder().create()
        var responseContent=mapOf("message" to "The requested resource could not be found")
        var response: Response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson(responseContent))
        var url= session.uri ?: return response

        if(session.method==Method.OPTIONS){
            response=newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
            addCorsHeaders(response,"")
            return response
        }

        if(url.startsWith("/server")){

            if(internalURI==null){
                return newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson(mapOf("message" to "No Root Folder Selected")))
            }

            url=url.substring(7,url.length)
            response = when (session.method){
                Method.GET-> handleGetRequest(url,session)
                Method.PUT -> handlePutRequest(url,session,sdCardURI,internalURI)
                Method.POST -> handlePostRequest(url,session,sdCardURI,internalURI)
                Method.DELETE -> handleDeleteRequest(url,session,sdCardURI,internalURI)
                else-> newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, MIME_JSON, gson.toJson(mapOf("message" to "Method Not Allowed")))
            }

        }
        else{

            if(!uiServerMode){
                response=staticBuiltUI(url)
            }else{
                if(uiServerLocation==null){
                    responseContent=mapOf("message" to "UI Server location not configured")
                    response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson(responseContent))

                }else{
                    response=networkService.proxyRequestToUiServer(uiServerLocation,url,session)
                }
            }

        }
        val backEndUrl=prefHandler.getBackEndUrl()
        if(!backEndUrl.isNullOrBlank()){
            addCorsHeaders(response,"")
        }
        return response
    }

    private fun staticBuiltUI(url: String): Response{
        var filePath = url.removePrefix("/") // Remove leading slash
        val gson=GsonBuilder().create()
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

        val response = fileHandlerHelper.serveStaticFile(filePath)
            ?: fileHandlerHelper.serveStaticFile("404.html")
            ?: newFixedLengthResponse(
                Status.NOT_FOUND,
                MIME_JSON,
                gson.toJson(mapOf("message" to "The requested resource could not be found"))
            )

        return response

        return response
    }

    private fun addCorsHeaders(response: Response,backEndUrl:String="\"http://10.0.0.106\"") {
        response.addHeader("Access-Control-Allow-Origin", "*" )
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
}

