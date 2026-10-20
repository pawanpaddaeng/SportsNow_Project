package com.example.cpen321application.network

import com.google.gson.annotations.SerializedName


data class UserProfileDto(
    @SerializedName("user_id") val userId: Int,
    val email: String,
    @SerializedName("f_name") val firstName: String,
    @SerializedName("l_name") val lastName: String,
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