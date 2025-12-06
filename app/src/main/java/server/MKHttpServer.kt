import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.BatteryManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import database.AppDatabase
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import helpers.FileHandlerHelper
import helpers.NetworkHelper
import helpers.SharedPreferencesHelper
import server.controller.FileController
import server.controller.PerformerController
import server.service.DBService
import server.service.EntityService
import server.service.FileService
import server.service.UserService
import server.service.NetworkService
import server.service.SessionManager
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.mapOf
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
    private val sessionManager = SessionManager()
    private val userService by lazy {UserService(database,sessionManager)}
    private val entityService by lazy {EntityService(database)}
    private val fileService by lazy { FileService(database,fileHandlerHelper,prefHandler,context) }
    private val fileController by lazy { FileController(context,database,fileService,networkService,dbService,prefHandler) }
    private val performerController by lazy { PerformerController(database, entityService,prefHandler) }
    private val MIME_JSON="application/json"
    private val activeServers = ConcurrentHashMap<String,Instant>()
    private val broadcastPort = 6789
    private var listeningSocket:DatagramSocket?=null
    private var broadcastingSocket:DatagramSocket?=null
    private val broadcastInterval = 5000L
    private val CLEANUP_THRESHOLD_SECONDS = 60L
    private var broadcastTimer: Timer? = null
    private var listeningThread: Thread? = null
    private val dbService by lazy { DBService(database) }


    override fun start() {
        if (!isRunning.get()) {
            super.start()
            isRunning.set(super.isAlive())
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
        broadcastingSocket = DatagramSocket()

        broadcastTimer= fixedRateTimer(period = broadcastInterval,daemon=true, name = "broadcastTimer") {

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
            try{
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val buffer = message.toByteArray()

                if (!isRunning.get()){
                    cancel()
                    return@fixedRateTimer
                }
                val packet = DatagramPacket(buffer, buffer.size, broadcastAddress, broadcastPort)
                broadcastingSocket?.send(packet)

            }catch (e:Exception){
                Log.e("MKServer Broadcast","Broadcasting "+getExceptionString(e))
            }

            try {
                val cleanupCutoffTime = Instant.now().minusSeconds(CLEANUP_THRESHOLD_SECONDS)
                val iterator = activeServers.entries.iterator()

                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val lastSeen = entry.value
                    if (lastSeen.isBefore(cleanupCutoffTime)) {
                        iterator.remove()
                    }
                }
            } catch (e: Exception) {
                Log.e("MKServer Cleanup", "Error during activeServers cleanup: ${getExceptionString(e)}")
            }
        }

    }

    private fun stopBroadcasting() {
        broadcastTimer?.cancel()
        broadcastingSocket?.close()
        broadcastTimer = null
    }

    private fun startListeningForBroadcasts() {

        listeningSocket = DatagramSocket(broadcastPort)
        listeningThread = Thread {
            val buffer = ByteArray(1024)

            try {

                while (isRunning.get()) {

                    val packet = DatagramPacket(buffer, buffer.size)
                    listeningSocket!!.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    val senderIP = packet.address.hostAddress
                    var deviceIP = networkHandler.getIpAddress(context)
                    if (deviceIP != null && senderIP != null) {
                        if (deviceIP == "null") {
                            deviceIP = "localhost"
                        }
                        if (message.contains("Server active at:") && senderIP != deviceIP) {
                            activeServers[senderIP] = Instant.now()
                        }
                    }
                }
            }
            catch (e: Exception) {
                Log.e("MKServer Broadcast ex","listening "+getExceptionString(e))
            }finally {
                listeningSocket?.close()
            }

        }.apply { start() }
    }

    private fun stopListeningForBroadcasts() {

        listeningSocket?.close()
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
            "/verify"->{
                try {
                    val authHeader = session.headers["authorization"]
                    val token = authHeader?.removePrefix("Bearer ")?.trim() ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(mapOf("message" to "Missing authorization header")))

                    val userId=sessionManager.validateSession(token)
                    return if(userId==null){
                        newFixedLengthResponse(Status.UNAUTHORIZED, MIME_JSON, gson.toJson(mapOf("message" to "Invalid or expired session")))
                    }else {
                        val user=userService.getUserById(userId);
                        if(user==null){
                            newFixedLengthResponse(Status.UNAUTHORIZED, MIME_JSON, gson.toJson(mapOf("message" to "Invalid or expired session")))
                        }else{
                            newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(mapOf("username" to user.userName,"token" to token)))
                        }
                    }
                } catch (e: Exception) {
                    return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(mapOf("message" to "An error occurred during verify", "error" to getExceptionString(e))))
                }
            }
            "/stats"->{
                try {

                    val hasExternalStorage = fileHandlerHelper.isSdCardAvailable()
                    val files = database.fileDao().getTotalFileCount()
                    val (totalInternal,freeInternal)=fileService.getInternalMemoryData()
                    val (totalExternal,freeExternal)=fileService.getExternalMemoryData()

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

    private fun handlePostRequest(url: String, session: IHTTPSession): Response{
        val gson: Gson = GsonBuilder().create()
        var response: Response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON,
            gson.toJson(mapOf("message" to "The requested resource could not be found")))

        when(url){
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
                    if (loginResult.success) {
                        val token=loginResult.token
                        return newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(mapOf("token" to token)))
                    } else {
                        return newFixedLengthResponse(Status.UNAUTHORIZED, MIME_JSON, gson.toJson(mapOf("message" to loginResult.message)))
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
            "/logout" -> {
                try {
                    val authHeader = session.headers["authorization"]
                    val token = authHeader?.removePrefix("Bearer ")?.trim() ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_JSON, gson.toJson(mapOf("message" to "Missing authorization header")))

                    sessionManager.invalidateSession(token)

                    return newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(mapOf("message" to "Logged out successfully")))

                } catch (e: Exception) {
                    return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(mapOf("message" to "An error occurred during logout", "error" to e.localizedMessage)))
                }
            }

        }
        return response
    }
    private fun handleDeleteRequest(url: String, session: IHTTPSession): Response{
        val gson: Gson = GsonBuilder().create()
        val response: Response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON,
            gson.toJson(mapOf("message" to "The requested resource could not be found")))
        return response
    }

    private fun handlePutRequest(url: String, session: IHTTPSession): Response {
        val gson: Gson = GsonBuilder().create()
        val response: Response = newFixedLengthResponse(
            Status.NOT_FOUND, MIME_JSON,
            gson.toJson(mapOf("message" to "The requested resource could not be found"))
        )
        return response
    }

    override fun serve(session: IHTTPSession): Response {

        val uiServerLocation=prefHandler.getFrontEndUrl()
        val uiServerMode=prefHandler.getUIServerMode()
        val gson: Gson = GsonBuilder().create()
        var responseContent=mapOf("message" to "The requested resource could not be found")
        var response: Response=newFixedLengthResponse(Status.NOT_FOUND, MIME_JSON, gson.toJson(responseContent))

        var url= session.uri

        if(url==null){
            addCorsHeaders(response,session.headers["origin"])
            return response
        }

        if(session.method==Method.OPTIONS){
            Log.d("MKServer","OPTIONS request received")
            response=newFixedLengthResponse(Status.OK, MIME_JSON, gson.toJson(mapOf("message" to "OK")))
            addCorsHeaders(response,session.headers["origin"])
            return response
        }

        val serverRequest=url.startsWith("/server")

        if(serverRequest){

            url=url.substring(7,url.length)

            val authResponse = authenticationMiddleware(session, url)
            if(authResponse!=null){
                Log.d("MKServer", "auth failed")
                addCorsHeaders(authResponse,session.headers["origin"])
                return authResponse
            }
            //Log.d("MKServer url", url)

            if(url.startsWith("/file") || url == "/thumbnail" || url == "/name" || url == "/scan" || url == "/cleanup"|| url == "/repair"){
                response= fileController.handleRequest(url, session)
                addCorsHeaders(response,session.headers["origin"])
                return response
            }

            if(url.startsWith("/performer")||url=="/deletePerformers"){
                response=performerController.handleRequest(url,session)
                addCorsHeaders(response,session.headers["origin"])
                return response
            }

            response=when (session.method){
                Method.GET-> handleGetRequest(url,session)
                Method.PUT -> handlePutRequest(url,session)
                Method.POST -> handlePostRequest(url,session)
                Method.DELETE -> handleDeleteRequest(url,session)
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


        addCorsHeaders(response,session.headers["origin"])
        return response
    }

    private fun authenticationMiddleware(session: IHTTPSession, url: String): Response? {
        val gson: Gson = GsonBuilder().create()
        val method = session.method

        // Skip authentication for these cases
        val isGetRequest = method == Method.GET
        val isLoginRequest = url == "/login" && method == Method.POST

        if (isGetRequest || isLoginRequest) {
            return null
        }

        val authHeader = session.headers["authorization"]
        if (authHeader.isNullOrBlank()) {
            return newFixedLengthResponse(Status.UNAUTHORIZED, MIME_JSON, gson.toJson(mapOf("message" to "Missing Authorization header")))
        }

        val token = authHeader.removePrefix("Bearer ").trim()
        if (token.isEmpty()) {
            return newFixedLengthResponse(Status.UNAUTHORIZED, MIME_JSON, gson.toJson(mapOf("message" to "Invalid Token")))
        }

        // Validate session using SessionManager
        val userSession = sessionManager.validateSession(token)
        if (userSession == null) {
            Log.d("MKServer", "session is invalid")
            return newFixedLengthResponse(Status.UNAUTHORIZED, MIME_JSON, gson.toJson(mapOf( "message" to "Invalid or expired session")))
        }

        return null
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
    }

    private fun addCorsHeaders(response: Response,url: String?=null) {

        val backEndUrl = prefHandler.getBackEndUrl()
        var allowOrigin = "*"

        if (url != null) {
            val host = extractHostname(url)

            if (host != null && isLocalNetwork(host)) {
                allowOrigin = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
            }
        }
        else if (backEndUrl != null) {
            allowOrigin = if (backEndUrl.startsWith("http://") || backEndUrl.startsWith("https://")) backEndUrl else "http://$backEndUrl"
        }


        response.addHeader("Access-Control-Allow-Origin", allowOrigin)
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
        response.addHeader("Access-Control-Allow-Credentials", "true")
    }

    private fun extractHostname(url: String): String? {
        return try {
            val uri = URI(url)
            uri.host ?: url
        } catch (e: Exception) {
            null
        }
    }

    private fun isLocalNetwork(host: String): Boolean {
        val localPatterns = listOf(
            Regex("""^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""),   // 10.x.x.x
            Regex("""^172\.(1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3}$"""), // 172.16.x.x - 172.31.x.x
            Regex("""^192\.168\.\d{1,3}\.\d{1,3}$""") // 192.168.x.x
        )
        return localPatterns.any { it.matches(host) }
    }

    private fun getExceptionString(exception:Exception):String{
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        exception.printStackTrace(printWriter)
        return stringWriter.toString()
    }
}

