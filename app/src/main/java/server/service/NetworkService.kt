package server.service


import android.util.Log
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class NetworkService {



    fun processMultipartFormData(inputStream: InputStream, boundary: String, outputStream: OutputStream,contentLength: Long):String {
        val boundaryBytes = "--$boundary\r\n".toByteArray(Charsets.UTF_8)
        val endBoundaryBytes = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        var buffer = ByteArray(16384)
        var bytesRead: Int=0
        var totalBytesRead: Long = 0
        var headersFound=false
        var fileName:String=""
        try {
            while (totalBytesRead < contentLength) {
                var offset=0
                bytesRead = try {
                    inputStream.read(buffer, 0, buffer.size)
                } catch (e: IOException) {
                    throw IOException("Network error or client disconnected", e)
                }

                if (bytesRead == -1) {
                    throw IOException("Unexpected end of stream. Client may have disconnected.")
                }

                totalBytesRead += bytesRead

                //remove the trailing boundary
                if(totalBytesRead>=contentLength){
                    if(!buffer.endsWith(endBoundaryBytes,bytesRead)){
                        throw Exception("Ending Boundary not found")
                    }
                    bytesRead -= endBoundaryBytes.size
                }

                if(!headersFound){
                    val extractedData=extractHeaders(buffer,boundaryBytes)
                    fileName=extractedData.second
                    offset=extractedData.first
                    headersFound=true;
                    bytesRead-=offset
                }
                //Log.d("upload portion","bytesRead:$bytesRead")
                outputStream.write(buffer, offset, bytesRead)

            }
            return fileName
        }
        catch (exception:Exception){
            throw exception
        }finally {
            outputStream.close()
        }

    }

    private fun extractHeaders(data: ByteArray, boundaryByes: ByteArray): Pair<Int,String> {

        if(!data.startsWith(boundaryByes)){
            throw Exception("Boundary not found")
        }

        val fileName= extractFileName(data) ?: throw Exception("FileName not found in from data")

        val allowedExtensions = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "webm")
        val extension = fileName.substringAfterLast('.', "")
        if (!allowedExtensions.contains(extension)) {
            throw Exception("Unsupported file type")
        }

        val breakerArray="\r\n\r\n".toByteArray()
        var index=data.indexOf(breakerArray)
        if(index==-1){
            throw Exception("Improper Format of Form Data")
        }
        index += breakerArray.size

        return Pair(index,fileName)
    }

    private fun extractFileName(data: ByteArray):String?{
        // No need to convert full data to string as we can find it in the beginning
        val slicedData=data.sliceArray(0 until 1000)
        val str=String(slicedData)
        val filenameRegex = Regex("""filename="([^"]+)"""")
        return filenameRegex.find(str)?.groupValues?.get(1)
    }

    private fun ByteArray.indexOf(target: ByteArray, startIndex: Int = 0): Int {
        for (i in startIndex..this.size - target.size) {
            if (this.sliceArray(i until i + target.size).contentEquals(target)) {
                return i
            }
        }
        return -1
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.endsWith(suffix: ByteArray,length:Int): Boolean {
        if (suffix.size > length) return false
        for (i in 0 until suffix.size) {
            if (this[length - suffix.size + i] != suffix[i]) {
                return false
            }
        }
        return true
    }


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