package com.example.cpen321application.core.network

import com.google.gson.JsonParseException
import retrofit2.Response
import java.io.IOException

/*  reads a reply's json: the body when the request worked, otherwise the error body,
    since error responses (4xx/5xx) carry the same json shape. Returns null if it can't be read
 */
fun <T> readReply(response: Response<T>, type: Class<T>): T? =
    response.body() ?: try {
        serverGson.fromJson(response.errorBody()?.string(), type)
    } catch (e: JsonParseException) {
        null
    } catch (e: IOException) {
        null
    }
