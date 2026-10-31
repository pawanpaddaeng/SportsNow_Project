package com.example.cpen321application.chat.socket

import com.example.cpen321application.BuildConfig
import com.example.cpen321application.chat.dto.ChatSocketEvent
import com.example.cpen321application.chat.dto.SendMessageRequest
import com.example.cpen321application.core.network.serverGson
import com.google.gson.JsonParseException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

// the port of the chat WebSocket when the app talks to the backend directly, without Caddy
private const val DIRECT_SOCKET_PORT = 8080
// the server closes the connection with this code when the user is no longer in the game
private const val CLOSE_NOT_A_PLAYER = 4403
private const val CLOSE_NORMAL = 1000
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404

private val client = OkHttpClient()

// wss://DOMAIN/ws through Caddy, or the backend's WebSocket port for a local http server
private fun chatSocketUrl(chatId: Int): String {
    val api = BuildConfig.API_BASE_URL.toHttpUrl()
    return if (api.isHttps) {
        "wss://${api.host}/ws?chat_id=$chatId"
    } else {
        "ws://${api.host}:$DIRECT_SOCKET_PORT?chat_id=$chatId"
    }
}

// what the connection reports. These run on OkHttp's thread, not the main thread
interface ChatConnectionListener {
    fun onOpen()
    fun onEvent(event: ChatSocketEvent)
    // canRetry is false when reconnecting won't help: the user isn't in the game or the chat is gone
    fun onDisconnected(canRetry: Boolean)
}

// a WebSocket to one chat, opened when created. Only stays open while the chat screen is shown
class ChatConnection(private val chatId: Int, jwt: String, private val listener: ChatConnectionListener) {
    private val webSocket: WebSocket = client.newWebSocket(
        Request.Builder()
            .url(chatSocketUrl(chatId))
            .header("Authorization", "Bearer $jwt")
            .build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = try {
                    serverGson.fromJson(text, ChatSocketEvent::class.java)
                } catch (e: JsonParseException) {
                    null
                }
                if (event != null) listener.onEvent(event)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(CLOSE_NORMAL, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected(canRetry = code != CLOSE_NOT_A_PLAYER)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                listener.onDisconnected(canRetry = code != HTTP_FORBIDDEN && code != HTTP_NOT_FOUND)
            }
        }
    )

    // false when the connection can't take it, e.g. it has closed
    fun send(text: String, clientId: String): Boolean =
        webSocket.send(serverGson.toJson(SendMessageRequest(chatId = chatId, text = text, clientId = clientId)))

    fun close() {
        webSocket.close(CLOSE_NORMAL, null)
    }
}
