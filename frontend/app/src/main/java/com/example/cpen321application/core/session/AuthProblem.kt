package com.example.cpen321application.core.session

import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException

/*  the JWT middleware answers 401 with msg expired_token, invalid_token or
    no_user. Returns that msg, or null if the request wasn't rejected for auth.
    Reads a copy of the error body so callers can still read it afterwards
 */
internal fun authProblem(response: Response<*>): String? {
    if (response.code() != 401) return null
    return try {
        val source = response.errorBody()?.source() ?: return "unauthorized"
        source.request(Long.MAX_VALUE)
        val text = source.buffer.clone().readUtf8()
        JSONObject(text).optString("msg").ifBlank { "unauthorized" }
    } catch (e: IOException) {
        "unauthorized"
    } catch (e: JSONException) {
        "unauthorized"
    }
}
