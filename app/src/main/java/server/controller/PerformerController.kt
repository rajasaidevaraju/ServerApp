package server.controller


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

    private val router = Router()

    init {
        router.get("/performers") { _, _ -> listPerformers() }
        router.get("/performersWithCount") { _, _ -> listPerformersWithCount() }
        router.post("/performers") { _, session -> addPerformers(session) }
        router.post("/deletePerformers") { _, session -> deletePerformers(session) }
        router.put("/performer/{performerId}") { params, session ->
            editPerformer(params.longPathParam("performerId"), session)
        }
    }

    override fun handleRequest(url: String, session: IHTTPSession): Response {
        return try {
            router.handle(url, session) ?: notFound()
        } catch (e: BadRequestException) {
            badRequest(e.message ?: "The request could not be processed due to invalid syntax")
        }
    }

    private fun listPerformers(): Response {
        val performers = database.actressDao().getAllActresses()
        val jsonContent: String = gson.toJson(performers)
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonContent)
    }

    private fun listPerformersWithCount(): Response {
        val performers = database.actressDao().getAllActressesWithCount()
        val jsonContent: String = gson.toJson(performers)
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_JSON, jsonContent)
    }

    private fun addPerformers(session: IHTTPSession): Response {
        val result = entityService.addEntities(EntityType.Performers, parsePostData(session))
        return if (result.success) {
            okRequest(result.message)
        } else {
            badRequest(result.message)
        }
    }

    private fun deletePerformers(session: IHTTPSession): Response {
        val result = entityService.deleteEntities(EntityType.Performers, parsePostData(session))
        return if (result.success) {
            okRequest(result.message)
        } else {
            badRequest(result.message)
        }
    }

    private fun editPerformer(performerId: Long, session: IHTTPSession): Response {
        try {
            val result = entityService.updateEntity(EntityType.Performers, parsePostData(session), performerId)
            return if (result.success) {
                okRequest(result.message)
            } else {
                badRequest(result.message)
            }
        } catch (exception: Exception) {
            return internalServerError(exception, "Update operation failed")
        }
    }

    private fun parsePostData(session: IHTTPSession): String? {
        val postBody = HashMap<String, String>()
        session.parseBody(postBody)
        return postBody["postData"]
    }
}