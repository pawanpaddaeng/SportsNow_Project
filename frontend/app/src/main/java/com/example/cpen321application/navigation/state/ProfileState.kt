package com.example.cpen321application.navigation.state

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cpen321application.user.repository.ProfileForm
import com.example.cpen321application.user.repository.ProfileResult
import com.example.cpen321application.user.repository.UserProfile
import com.example.cpen321application.user.repository.fetchMyProfile
import com.example.cpen321application.user.repository.updateMyProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// the signed in user's profile, for the Profile and Edit Profile screens
class ProfileState(private val context: Context, private val scope: CoroutineScope) {
    var profile by mutableStateOf<UserProfile?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    fun refresh() {
        if (isLoading) return
        isLoading = true
        loadJob = scope.launch {
            try {
                when (val result = fetchMyProfile(context)) {
                    is ProfileResult.Loaded -> {
                        profile = result.profile
                        error = null
                    }
                    is ProfileResult.Failed -> error = result.message
                }
            } finally {
                isLoading = false
            }
        }
    }

    // onSaved runs once the server saved the changes
    fun save(form: ProfileForm, onSaved: () -> Unit) {
        if (isSaving) return
        isSaving = true
        saveError = null
        saveJob = scope.launch {
            val result = try {
                updateMyProfile(context, form)
            } finally {
                isSaving = false
            }
            when (result) {
                is ProfileResult.Loaded -> {
                    profile = result.profile
                    error = null
                    Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                    onSaved()
                }
                is ProfileResult.Failed -> saveError = result.message
            }
        }
    }

    // also cancels a load or save in progress so its result can't fill the cleared state
    fun clear() {
        loadJob?.cancel()
        saveJob?.cancel()
        isLoading = false
        isSaving = false
        profile = null
        error = null
        saveError = null
    }
}
