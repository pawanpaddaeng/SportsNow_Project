package com.example.cpen321application.navigation.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cpen321application.core.showToast
import com.example.cpen321application.review.model.Badge
import com.example.cpen321application.review.repository.BadgesResult
import com.example.cpen321application.review.repository.ReputationScoreResult
import com.example.cpen321application.review.repository.fetchMyBadges
import com.example.cpen321application.review.repository.fetchMyReputationScore
import com.example.cpen321application.user.repository.DeleteAccountResult
import com.example.cpen321application.user.repository.ProfileForm
import com.example.cpen321application.user.repository.ProfileResult
import com.example.cpen321application.user.repository.UserProfile
import com.example.cpen321application.user.repository.deleteMyAccount
import com.example.cpen321application.user.repository.fetchMyProfile
import com.example.cpen321application.user.repository.updateMyProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// the signed in user's profile, reputation score and badges, for the Profile and Edit Profile screens
class ProfileState(private val context: Context, private val scope: CoroutineScope) {
    var profile by mutableStateOf<UserProfile?>(null)
        private set
    // null until it first loads, and while the user isn't rated yet
    var reputationScore by mutableStateOf<Double?>(null)
        private set
    var isScoreLoaded by mutableStateOf(false)
        private set
    // the badges the user earned, null until they first load
    var badges by mutableStateOf<List<Badge>?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
    var isDeleting by mutableStateOf(false)
        private set
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var deleteJob: Job? = null

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
                // the score can change after any game, so it's read every time. The last one stays on failure
                when (val result = fetchMyReputationScore(context)) {
                    is ReputationScoreResult.Loaded -> {
                        reputationScore = result.score
                        isScoreLoaded = true
                    }
                    is ReputationScoreResult.Failed -> error = error ?: result.message
                }
                // new badges can be earned after any game, friend or rating. The last ones stay on failure
                when (val result = fetchMyBadges(context)) {
                    is BadgesResult.Loaded -> badges = result.badges
                    is BadgesResult.Failed -> error = error ?: result.message
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
                    showToast(context, "Profile updated")
                    onSaved()
                }
                is ProfileResult.Failed -> saveError = result.message
            }
        }
    }

    // onDeleted runs once the server deleted the account, a failure is shown on Profile
    fun deleteAccount(onDeleted: () -> Unit) {
        if (isDeleting) return
        isDeleting = true
        deleteJob = scope.launch {
            val result = try {
                deleteMyAccount(context)
            } finally {
                isDeleting = false
            }
            when (result) {
                DeleteAccountResult.Deleted -> onDeleted()
                is DeleteAccountResult.Failed -> error = result.message
            }
        }
    }

    // also cancels a load, save or delete in progress so its result can't fill the cleared state
    fun clear() {
        loadJob?.cancel()
        saveJob?.cancel()
        deleteJob?.cancel()
        isLoading = false
        isSaving = false
        isDeleting = false
        profile = null
        reputationScore = null
        isScoreLoaded = false
        badges = null
        error = null
        saveError = null
    }
}
