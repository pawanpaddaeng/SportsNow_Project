package com.example.cpen321application.user

import com.example.cpen321application.user.repository.ProfileForm
import java.time.Year

const val MIN_PROFILE_AGE = 19
const val MAX_PROFILE_AGE = 100

const val MAX_NAME_LENGTH = 100

const val COMPULSORY_FIELDS_MESSAGE = "First Name, Last Name and Birth Year fields are compulsory"

private val ALLOWED_NAME_CHARACTER = Regex("[\\p{L}\\p{M} '.-]")

sealed interface ProfileCheck {
    data class Valid(val form: ProfileForm) : ProfileCheck
    data class Invalid(val message: String) : ProfileCheck
}

fun illegalNameCharacters(name: String): List<String> =
    name.codePoints().toArray()
        .map { String(Character.toChars(it)) }
        .filterNot { ALLOWED_NAME_CHARACTER.matches(it) }
        .distinct()

fun checkProfile(
    firstName: String,
    lastName: String,
    birthYear: Int?,
    currentYear: Int = Year.now().value
): ProfileCheck {
    val first = firstName.trim()
    val last = lastName.trim()
    if (first.isEmpty() || last.isEmpty() || birthYear == null) {
        return ProfileCheck.Invalid(COMPULSORY_FIELDS_MESSAGE)
    }

    val error = nameError(first, "First Name")
        ?: nameError(last, "Last Name")
        ?: birthYearError(birthYear, currentYear)
    return if (error == null) ProfileCheck.Valid(ProfileForm(first, last, birthYear)) else ProfileCheck.Invalid(error)
}

private fun nameError(name: String, label: String): String? {
    val illegal = illegalNameCharacters(name)
    return when {
        name.length > MAX_NAME_LENGTH -> "$label must be at most $MAX_NAME_LENGTH characters"
        illegal.isNotEmpty() -> "$label contains characters that aren't allowed: ${illegal.joinToString(" ")}"
        else -> null
    }
}

private fun birthYearError(birthYear: Int, currentYear: Int): String? {
    val youngest = currentYear - MIN_PROFILE_AGE
    val oldest = currentYear - MAX_PROFILE_AGE
    return if (birthYear in oldest..youngest) null else "Birth Year must be between $oldest and $youngest"
}