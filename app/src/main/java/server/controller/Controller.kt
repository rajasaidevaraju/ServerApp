package server.controller

import fi.iki.elonen.NanoHTTPD
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import helpers.SharedPreferencesHelper
import java.io.PrintWriter
import java.io.StringWriter

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