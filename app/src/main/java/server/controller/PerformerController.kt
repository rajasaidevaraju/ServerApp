package server.controller


import android.net.Uri
import database.AppDatabase
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import helpers.SharedPreferencesHelper
import server.service.EntityService
import server.service.EntityType

class PerformerController(
    private val database: AppDatabase,
    private val entityService: EntityService,
    prefHandler: SharedPreferencesHelper
) : BaseController(prefHandler) {

    override fun handleRequest(url: String, session: IHTTPSession): Response {

        return when (session.method) {
            NanoHTTPD.Method.GET -> handleGetRequest(url, session)
            NanoHTTPD.Method.POST -> handlePostRequest(url, session)
            NanoHTTPD.Method.PUT -> handlePutRequest(url, session)
            NanoHTTPD.Method.DELETE -> handleDeleteRequest(url, session)
            else -> notFound()
        }
    }

    private fun handleGetRequest(url: String, session: IHTTPSession): Response {
        when (url) {
            "/performers" -> {
                val performers = database.actressDao().getAllActressesWithCount()
                val jsonContent: String = gson.toJson(performers)
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonContent)
            }
            else -> return notFound()
        }
    }

    private fun handlePostRequest(url: String, session: IHTTPSession): Response {
        val postBody = HashMap<String, String>()
        session.parseBody(postBody)
        val postData = postBody["postData"]

        return when (url) {
            "/performers" -> {
                val result = entityService.addEntities(EntityType.Performers, postData)
                if (result.success) {
                    okRequest(result.message)
                } else {
                    badRequest(result.message)
                }
            }
            "/deletePerformers" -> {
                val result = entityService.deleteEntities(EntityType.Performers, postData)
                if (result.success) {
                    okRequest(result.message)
                } else {
                    badRequest(result.message)
                }
            }
            else -> notFound()
        }
    }

    private fun handlePutRequest(url: String, session: IHTTPSession): Response {
        when {
            url.startsWith("/performer") -> {
                val postBody = HashMap<String, String>()
                session.parseBody(postBody)
                val postData = postBody["postData"]
                return editPerformer(postData,session)
            }
            else -> return notFound()
        }
    }

    private fun handleDeleteRequest(url: String, session: IHTTPSession): Response {
        return notFound()
    }

    private fun editPerformer(postData:String?,session: IHTTPSession):Response{
        try {
            val uri = session.uri.split("/")
            val id = uri[uri.size - 1].toLongOrNull()
            val result = entityService.updateEntity(EntityType.Performers, postData, id)
            return if (result.success) {
                okRequest(result.message)
            } else {
                badRequest(result.message)
            }
        } catch (exception: Exception) {
            return internalServerError(exception, "Update operation failed")
        }
    }
}