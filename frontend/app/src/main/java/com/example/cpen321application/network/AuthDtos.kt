package com.example.cpen321application.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response

//add datatypes for sending and receiving information (used by gson for parsing)
data class User(
    @field:Required val firstName: String,
    @field:Required val lastName: String
)

data class AuthResponse(
    val message: String?,
    val user: User?,
    val error: String?
)

data class AuthRequest(
    val idToken : String?
)

// body for POST api/auth/signup, field names match the backend spec
data class SignUpRequest(
    @SerializedName("id_token") val idToken: String,
    val email: String,
    @SerializedName("f_name") val firstName: String,
    @SerializedName("l_name") val lastName: String,
    val birthYear: Int
)

// body for POST api/auth/login
data class LoginRequest(
    @SerializedName("id_token") val idToken: String
)

/*  response from api/auth/signup and api/auth/login:
    msg is "signedIn" with a jwtToken on success, "already_exists" (sign up) or
    "no_user" (login) for account problems, otherwise an error code
    (invalid_request, invalid_token, server_error) with an error description
 */
data class AuthTokenResponse(
    val msg: String?,
    @SerializedName("jwt_token") val jwtToken: String?,
    val error: String?
)

// body for POST api/register_notif, the JWT goes in the Authorization header
data class RegisterNotifRequest(
    @SerializedName("fcm_token") val fcmToken: String
)

/*  reads the json reply of the auth style endpoints, error responses (4xx/5xx)
    carry the same json shape in the error body. Returns null if it can't be read
 */
fun readAuthResponse(response: Response<AuthTokenResponse>): AuthTokenResponse? =
    readReply(response, AuthTokenResponse::class.java)
