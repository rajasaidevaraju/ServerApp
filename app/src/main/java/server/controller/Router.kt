package server.controller

import fi.iki.elonen.NanoHTTPD

class BadRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun Map<String, String>.longPathParam(name: String): Long =
    this[name]?.toLongOrNull() ?: throw BadRequestException("Missing or invalid $name in URL")

typealias RouteHandler = (pathParams: Map<String, String>, session: NanoHTTPD.IHTTPSession) -> NanoHTTPD.Response

/**
 * Minimal route table. Patterns are literal paths with optional {name} segments,
 * e.g. "/file/{fileId}/rename"; matched segment values are passed to the handler
 * as pathParams.
 */
class Router {

    private data class Route(
        val method: NanoHTTPD.Method,
        val regex: Regex,
        val paramNames: List<String>,
        val handler: RouteHandler
    )

    private val routes = mutableListOf<Route>()

    fun get(pattern: String, handler: RouteHandler) = add(NanoHTTPD.Method.GET, pattern, handler)
    fun post(pattern: String, handler: RouteHandler) = add(NanoHTTPD.Method.POST, pattern, handler)
    fun put(pattern: String, handler: RouteHandler) = add(NanoHTTPD.Method.PUT, pattern, handler)
    fun delete(pattern: String, handler: RouteHandler) = add(NanoHTTPD.Method.DELETE, pattern, handler)

    fun add(method: NanoHTTPD.Method, pattern: String, handler: RouteHandler) {
        val paramNames = mutableListOf<String>()
        val regexPattern = pattern.split("/").joinToString("/") { segment ->
            if (segment.startsWith("{") && segment.endsWith("}")) {
                paramNames.add(segment.substring(1, segment.length - 1))
                "([^/]+)"
            } else {
                Regex.escape(segment)
            }
        }
        routes.add(Route(method, Regex("^$regexPattern$"), paramNames, handler))
    }

    /** Returns null when no route matches the method and url. */
    fun handle(url: String, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response? {
        for (route in routes) {
            if (route.method != session.method) continue
            val match = route.regex.matchEntire(url) ?: continue
            val pathParams = route.paramNames.zip(match.groupValues.drop(1)).toMap()
            return route.handler(pathParams, session)
        }
        return null
    }
}
