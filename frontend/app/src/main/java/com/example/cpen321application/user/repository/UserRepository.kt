package com.example.cpen321application.user.repository

import android.content.Context
import android.util.Base64
import com.example.cpen321application.core.NETWORK_MESSAGE
import com.example.cpen321application.core.SESSION_ENDED_MESSAGE
import com.example.cpen321application.core.network.ServerApi
import com.example.cpen321application.core.network.readReply
import com.example.cpen321application.core.session.Session
import com.example.cpen321application.user.dto.LoginRequest
import com.example.cpen321application.user.dto.ProfileResponse
import com.example.cpen321application.user.dto.SignUpRequest
import com.example.cpen321application.user.dto.UpdateProfileRequest
import com.example.cpen321application.user.dto.UserProfileDto
import com.example.cpen321application.user.dto.readAuthResponse
import com.google.gson.JsonParseException
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val LOAD_FAILED_MESSAGE = "Couldn't load your profile, try again"
private const val SAVE_FAILED_MESSAGE = "Couldn't save your profile, try again"
private const val SIGN_UP_FAILED_MESSAGE = "Could not create account, try again"

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

data class SignUpDetails(
    val idToken: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val birthYear: Int
)

// name and email read from the Google id token so the sign up form can be prefilled
data class GoogleProfile(
    val email: String?,
    val givenName: String?,
    val familyName: String?
)

sealed interface LoginResult {
    data class SignedIn(val jwtToken: String) : LoginResult
    data object NoUser : LoginResult
    data object Failed : LoginResult
}

sealed interface SignUpResult {
    data class Created(val jwtToken: String) : SignUpResult
    data object AlreadyExists : SignUpResult
    data class Failed(val message: String) : SignUpResult
}

sealed interface ProfileResult {
    data class Loaded(val profile: UserProfile) : ProfileResult
    data class Failed(val message: String) : ProfileResult
}

// the server answers "no_user" (404) when the Google account needs to sign up first
suspend fun login(googleToken: String): LoginResult {
    return try {
        val body = readAuthResponse(ServerApi.retrofitService.login(LoginRequest(idToken = googleToken)))
        when {
            body?.msg == "signedIn" && body.jwtToken != null -> LoginResult.SignedIn(body.jwtToken)
            body?.msg == "no_user" -> LoginResult.NoUser
            else -> LoginResult.Failed
        }
    } catch (e: IOException) {
        LoginResult.Failed
    } catch (e: JsonParseException) {
        LoginResult.Failed
    }
}

// the server answers "already_exists" (409) when the Google account already has an account
suspend fun signUp(details: SignUpDetails): SignUpResult {
    val email = details.email
        ?: return SignUpResult.Failed("Could not read the email of your Google account, verify again")

    return try {
        val response = ServerApi.retrofitService.signUp(
            SignUpRequest(
                idToken = details.idToken,
                email = email,
                firstName = details.firstName,
                lastName = details.lastName,
                birthYear = details.birthYear
            )
        )
        val body = readAuthResponse(response)
        when {
            body?.msg == "signedIn" && body.jwtToken != null -> SignUpResult.Created(body.jwtToken)
            body?.msg == "already_exists" || response.code() == 409 -> SignUpResult.AlreadyExists
            body?.msg == "invalid_token" ->
                SignUpResult.Failed("Your Google verification expired, verify with Google again")
            else -> SignUpResult.Failed(body?.error ?: SIGN_UP_FAILED_MESSAGE)
        }
    } catch (e: IOException) {
        SignUpResult.Failed("Cannot Reach Server")
    } catch (e: JsonParseException) {
        SignUpResult.Failed(SIGN_UP_FAILED_MESSAGE)
    }
}

/*  the id token is a JWT whose middle part is base64 encoded JSON with the account's
    profile claims. Only used for prefilling, the backend verifies the token itself
 */
fun readGoogleProfile(idToken: String): GoogleProfile {
    val empty = GoogleProfile(null, null, null)
    val payload = idToken.split(".").getOrNull(1) ?: return empty
    return try {
        val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        val json = JSONObject(String(Base64.decode(payload, flags)))
        GoogleProfile(
            email = json.optString("email").ifBlank { null },
            givenName = json.optString("given_name").ifBlank { null },
            familyName = json.optString("family_name").ifBlank { null }
        )
    } catch (e: IllegalArgumentException) {
        // not valid base64
        empty
    } catch (e: JSONException) {
        empty
    }
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
        toProfileResult(readReply(response, ProfileResponse::class.java), failureMessage)
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
