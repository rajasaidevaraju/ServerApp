package server.service


import android.util.Log
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class NetworkService {

    fun proxyRequestToUiServer(uiServerLocation: String, uri: String, session: IHTTPSession): Response {
        Log.d("MKServer UI REQUEST START", "http://$uiServerLocation$uri")
        val uiServerUrl: URL
        try {
            uiServerUrl = URL("http://$uiServerLocation$uri")
        } catch (e: MalformedURLException) {
            Log.e("MKServer UI REQUEST", "Invalid URL", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Invalid URL")
        }

        val connection = try {
            uiServerUrl.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            Log.e("MKServer UI REQUEST", "Failed to open connection", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to open connection to UI Server")
        }

        try {
            connection.requestMethod = session.method.name
            session.headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            connection.doInput = true
            connection.doOutput = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            val inputStream: InputStream? = if (responseCode in 200..399) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            if (inputStream == null) {
                Log.e("MKServer UI REQUEST", "Input stream is null")
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
            }

            val contentType = connection.contentType ?: "application/octet-stream"
            val response = newFixedLengthResponse(Response.Status.lookup(responseCode), contentType, inputStream, connection.contentLength.toLong())
            connection.headerFields.forEach { (key, value) ->
                if (key != null && value != null) {
                    if (response.getHeader(key) == null) {
                        // If the header doesn't exist, add it
                        response.addHeader(key, value.joinToString("; "))
                    }
                }
            }
            Log.d("MKServer UI REQUEST END", "http://$uiServerLocation$uri with response code:${Response.Status.lookup(responseCode)}")
            return response
        } catch (exception: Exception) {
            val jsonResponse = JSONObject()
            jsonResponse.put("message", "Error occurred when fetching response from UI Server")
            jsonResponse.put("error message", exception.toString())
            exception.printStackTrace()
            val jsonContent = jsonResponse.toString()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", jsonContent)
        } finally {
            //connection.disconnect()
        }
    }

}