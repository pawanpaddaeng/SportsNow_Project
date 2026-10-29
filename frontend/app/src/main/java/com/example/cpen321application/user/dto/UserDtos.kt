package com.example.cpen321application.user.dto

import com.example.cpen321application.core.network.Required
import com.example.cpen321application.core.network.readReply
import com.google.gson.annotations.SerializedName
import retrofit2.Response

// body for POST api/auth/signup, field names match the backend spec
data class SignUpRequest(
    @SerializedName("id_token") val idToken: String,
    val email: String,
    @SerializedName("f_name") val firstName: String,
    @SerializedName("l_name") val lastName: String,
    val birthYear: Int
)

data class LoginRequest(
    @SerializedName("id_token") val idToken: String
)

/*  reply from api/auth/signup, api/auth/login and api/register_notif:
    msg is "signedIn" with a jwtToken on success, "already_exists" (sign up) or
    "no_user" (login) for account problems, otherwise an error code with an error description
 */
data class AuthTokenResponse(
    val msg: String?,
    @SerializedName("jwt_token") val jwtToken: String?,
    val error: String?
)

// the JWT goes in the Authorization header
data class RegisterNotifRequest(
    @SerializedName("fcm_token") val fcmToken: String
)

fun readAuthResponse(response: Response<AuthTokenResponse>): AuthTokenResponse? =
    readReply(response, AuthTokenResponse::class.java)

data class UserProfileDto(
    @SerializedName("user_id") val userId: Int,
    @field:Required val email: String,
    @field:Required @SerializedName("f_name") val firstName: String,
    @field:Required @SerializedName("l_name") val lastName: String,
    val birthYear: Int,
    @SerializedName("created_at") val createdAt: String?
)

data class UpdateProfileRequest(
    @SerializedName("f_name") val firstName: String,
    @SerializedName("l_name") val lastName: String,
    val birthYear: Int
)

data class ProfileResponse(
    val msg: String?,
    val user: UserProfileDto?,
    val error: String?
)
