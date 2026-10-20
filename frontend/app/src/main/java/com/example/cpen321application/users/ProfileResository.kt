package com.example.cpen321application.users

import android.content.Context
import com.example.cpen321application.auth.Session
import com.example.cpen321application.network.ProfileResponse
import com.example.cpen321application.network.ServerApi
import com.example.cpen321application.network.UpdateProfileRequest
import com.example.cpen321application.network.UserProfileDto
import com.example.cpen321application.network.serverGson
import com.google.gson.JsonParseException
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val SESSION_ENDED_MESSAGE = "Your session ended, please sign in again"
private const val NETWORK_MESSAGE = "Couldn't reach the server, try again"
private const val LOAD_FAILED_MESSAGE = "Couldn't load your profile, try again"
private const val SAVE_FAILED_MESSAGE = "Couldn't save your profile, try again"

private val MEMBER_SINCE_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

data class UserProfile(
    val userId: Int,
    val email: String,
    val firstName: String,
    val lastName: String,
    val birthYear: Int,
    val memberSince: String?
)

data class ProfileForm(
    val firstName: String,
    val lastName: String,
    val birthYear: Int
)

sealed interface ProfileResult {
    data class Loaded(val profile: UserProfile) : ProfileResult
    data class Failed(val message: String) : ProfileResult
}

suspend fun fetchMyProfile(context: Context): ProfileResult =
    requestProfile(context, LOAD_FAILED_MESSAGE) { authorization ->
        ServerApi.retrofitService.getMyProfile(authorization = authorization)
    }

suspend fun updateMyProfile(context: Context, form: ProfileForm): ProfileResult {
    val request = UpdateProfileRequest(
        firstName = form.firstName,
        lastName = form.lastName,
        birthYear = form.birthYear
    )
    return requestProfile(context, SAVE_FAILED_MESSAGE) { authorization ->
        ServerApi.retrofitService.updateMyProfile(authorization = authorization, request = request)
    }
}

private suspend fun requestProfile(
    context: Context,
    failureMessage: String,
    request: suspend (authorization: String) -> Response<ProfileResponse>
): ProfileResult = try {
    val response = Session.call(context, request)
    if (response == null) {
        ProfileResult.Failed(SESSION_ENDED_MESSAGE)
    } else {
        toProfileResult(readProfileReply(response), failureMessage)
    }
} catch (e: IOException) {
    ProfileResult.Failed(NETWORK_MESSAGE)
} catch (e: JsonParseException) {
    ProfileResult.Failed(failureMessage)
}

private fun toProfileResult(reply: ProfileResponse?, failureMessage: String): ProfileResult {
    val user = reply?.user
    return when {
        reply?.msg == "success" && user != null -> ProfileResult.Loaded(user.toUserProfile())
        reply?.msg == "invalid_request" && reply.error != null -> ProfileResult.Failed(reply.error)
        else -> ProfileResult.Failed(failureMessage)
    }
}

private fun readProfileReply(response: Response<ProfileResponse>): ProfileResponse? =
    response.body() ?: try {
        serverGson.fromJson(response.errorBody()?.string(), ProfileResponse::class.java)
    } catch (e: JsonParseException) {
        null
    } catch (e: IOException) {
        null
    }

fun UserProfileDto.toUserProfile(): UserProfile = UserProfile(
    userId = userId,
    email = email,
    firstName = firstName,
    lastName = lastName,
    birthYear = birthYear,
    memberSince = createdAt?.let { formatMemberSince(it) }
)

private fun formatMemberSince(isoTime: String): String? = try {
    MEMBER_SINCE_FORMAT.format(Instant.parse(isoTime).atZone(ZoneId.systemDefault()))
} catch (e: DateTimeParseException) {
    null
}