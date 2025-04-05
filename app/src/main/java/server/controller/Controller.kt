package server.controller

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fi.iki.elonen.NanoHTTPD
import helpers.SharedPreferencesHelper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets

interface Controller {
    fun handleRequest(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response
}

abstract class BaseController(private val prefHandler: SharedPreferencesHelper) : Controller{
    protected val MIME_JSON = "application/json"
    protected val gson: Gson = GsonBuilder().create()

    protected fun notFound(message:String= "The requested resource could not be found"): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, MIME_JSON, gson.toJson(mapOf("message" to message)))
    }
    protected fun badRequest(message: String="The request could not be processed due to invalid syntax"): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, MIME_JSON, gson.toJson(mapOf("message" to message)))
    }

    protected fun okRequest(message: String="Success"): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, MIME_JSON, gson.toJson(mapOf("message" to message)))
    }

    protected fun parseRequestBody(session: NanoHTTPD.IHTTPSession):HashMap<String,String>{
        val res=HashMap<String,String>();
        val contentLength=session.headers["content-length"]
        var lengthInBytes:Long=0;
        val inputStream=session.inputStream
        if (contentLength!=null) {
            lengthInBytes=contentLength.toLong();
        }
        if(lengthInBytes>1024){
            throw Exception("Request body too large")
        }
        if(lengthInBytes<=0){
            return res
        }

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var bytesRead: Int = 0
        var totalBytesRead: Long = 0

        while (totalBytesRead < lengthInBytes) {
            val remainingBytes = (lengthInBytes - totalBytesRead).toInt()
            val readLength = minOf(buffer.size, remainingBytes)

            bytesRead = try {
                inputStream.read(buffer, 0, readLength)
            } catch (e: java.io.IOException) {
                throw Exception("Error reading request body", e)
            }

            if (bytesRead == -1) {
                throw Exception("Unexpected end of stream. Expected $contentLength bytes, but got $totalBytesRead")
            }

            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
        }

        val requestBody = outputStream.toString(StandardCharsets.UTF_8.name())
        try {
            val jsonObject = JSONObject(requestBody)
            for (key in jsonObject.keys()) {
                val value = jsonObject.getString(key)
                res[key] = value
            }
        } catch (e: org.json.JSONException) {
            throw Exception("Invalid JSON in request body", e)
        }

        return res

    }

    protected fun internalServerError(exception: Exception?, message: String="Internal Server Error"): NanoHTTPD.Response {

        val messageMap = if (exception != null) {
            mapOf("message" to message, "stackTrace" to getExceptionString(exception))
        } else {
            mapOf("message" to message)
        }

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_JSON, gson.toJson(messageMap))
    }

    private fun getExceptionString(exception:Exception):String{
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        exception.printStackTrace(printWriter)
        return stringWriter.toString()
    }

    protected fun getSdCardURI() = prefHandler.getSDCardURI()

    protected fun getInternalURI() = prefHandler.getInternalURI()

}