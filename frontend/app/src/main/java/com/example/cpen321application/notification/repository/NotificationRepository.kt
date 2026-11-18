package com.example.cpen321application.notification.repository

import android.content.Context
import com.example.cpen321application.core.NETWORK_MESSAGE
import com.example.cpen321application.core.SESSION_ENDED_MESSAGE
import com.example.cpen321application.core.network.ServerApi
import com.example.cpen321application.core.session.Session
import com.example.cpen321application.notification.model.NotificationsPage
import com.example.cpen321application.notification.model.toNotificationsPage
import com.google.gson.JsonParseException
import java.io.IOException

private const val COULD_NOT_LOAD = "Couldn't load your notifications, try again"

sealed interface NotificationsResult {
    data class Loaded(val page: NotificationsPage) : NotificationsResult
    data class Failed(val message: String) : NotificationsResult
}

// the user's newest notifications with the counts for the badge and the Friend requests button
suspend fun fetchNotifications(context: Context): NotificationsResult {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.notificationService.getNotifications(authorization)
        } ?: return NotificationsResult.Failed(SESSION_ENDED_MESSAGE)
        val reply = response.body()
        if (response.isSuccessful && reply?.msg == "success") {
            NotificationsResult.Loaded(reply.toNotificationsPage())
        } else {
            NotificationsResult.Failed(COULD_NOT_LOAD)
        }
    } catch (e: IOException) {
        NotificationsResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        NotificationsResult.Failed(COULD_NOT_LOAD)
    }
}

/*  tells the server the user saw all their notifications. Returns whether it worked, nothing
    is shown when it didn't: they just stay unread until the next time
 */
suspend fun markNotificationsRead(context: Context): Boolean {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.notificationService.markNotificationsRead(authorization)
        } ?: return false
        response.isSuccessful && response.body()?.msg == "success"
    } catch (e: IOException) {
        false
    } catch (e: JsonParseException) {
        false
    }
}
