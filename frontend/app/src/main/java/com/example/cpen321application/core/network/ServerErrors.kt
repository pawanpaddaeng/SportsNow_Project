package com.example.cpen321application.core.network

import com.google.gson.JsonParseException
import java.io.IOException

/*  runs a repository's server call and turns the two ways it can fail into its result:
    networkFailure when the server can't be reached, unreadableReply when its reply can't be
    read. Inline, so the call can return early from the repository function as before
 */
inline fun <R> catchingServerErrors(networkFailure: R, unreadableReply: R, call: () -> R): R =
    try {
        call()
    } catch (e: IOException) {
        networkFailure
    } catch (e: JsonParseException) {
        unreadableReply
    }
