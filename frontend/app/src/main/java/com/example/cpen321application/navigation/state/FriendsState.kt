package com.example.cpen321application.navigation.state

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cpen321application.friend.model.FriendPerson
import com.example.cpen321application.friend.model.FriendRequestItem
import com.example.cpen321application.friend.repository.FriendResult
import com.example.cpen321application.friend.repository.decideFriendRequest
import com.example.cpen321application.friend.repository.fetchFriendRequests
import com.example.cpen321application.friend.repository.fetchFriends
import com.example.cpen321application.friend.repository.searchUsers
import com.example.cpen321application.friend.repository.sendFriendRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// how long typing has to pause before searching, so every letter doesn't send a request
private const val SEARCH_DELAY_MILLIS = 300L

// the server accepts search text up to this long
private const val MAX_QUERY_LENGTH = 100

// the Friends tab: the search, the requests waiting for an answer and the friends
class FriendsState(private val context: Context, private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var friends by mutableStateOf(emptyList<FriendPerson>())
        private set
    var requests by mutableStateOf(emptyList<FriendRequestItem>())
        private set
    var searchResults by mutableStateOf(emptyList<FriendPerson>())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isSearching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var busyRequestIds by mutableStateOf(emptySet<Int>())
        private set
    var busyUserIds by mutableStateOf(emptySet<Int>())
        private set
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val changeJobs = mutableListOf<Job>()

    // on failure the last loaded lists stay visible with the error above them
    fun refresh() {
        if (isLoading) return
        isLoading = true
        loadJob = scope.launch {
            try {
                val loadedFriends = fetchFriends(context)
                val loadedRequests = fetchFriendRequests(context)
                if (loadedFriends is FriendResult.Done) friends = loadedFriends.value
                if (loadedRequests is FriendResult.Done) requests = loadedRequests.value
                error = (loadedFriends as? FriendResult.Failed)?.message
                    ?: (loadedRequests as? FriendResult.Failed)?.message
            } finally {
                isLoading = false
            }
        }
    }

    // searches as the user types, the previous results stay until the new ones arrive
    fun updateQuery(text: String) {
        query = text.take(MAX_QUERY_LENGTH)
        searchJob?.cancel()
        searchError = null
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
        } else {
            search(SEARCH_DELAY_MILLIS)
        }
    }

    fun retrySearch() {
        searchJob?.cancel()
        searchError = null
        search(waitMillis = 0L)
    }

    private fun search(waitMillis: Long) {
        val text = query.trim()
        isSearching = true
        searchJob = scope.launch {
            try {
                delay(waitMillis)
                val result = searchUsers(context, text)
                // the user may have typed more while this was on its way
                if (text == query.trim()) {
                    when (result) {
                        is FriendResult.Done -> searchResults = result.value
                        is FriendResult.Failed -> searchError = result.message
                    }
                }
            } finally {
                if (text == query.trim()) isSearching = false
            }
        }
    }

    fun addFriend(person: FriendPerson) {
        busyUserIds = busyUserIds + person.userId
        launchChange {
            val result = try {
                sendFriendRequest(context, person.userId)
            } finally {
                busyUserIds = busyUserIds - person.userId
            }
            val message = when (result) {
                is FriendResult.Done -> "Friend request sent to ${person.name}"
                is FriendResult.Failed -> result.message
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // the card goes away once the server saved the answer
    fun answer(request: FriendRequestItem, accept: Boolean) {
        busyRequestIds = busyRequestIds + request.requestId
        launchChange {
            val result = try {
                decideFriendRequest(context, request.requestId, accept)
            } finally {
                busyRequestIds = busyRequestIds - request.requestId
            }
            when (result) {
                is FriendResult.Done -> {
                    requests = requests.filterNot { it.requestId == request.requestId }
                    if (accept) friends = (friends + request.sender).sortedBy { it.name }
                }
                is FriendResult.Failed -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    // e.g. it was already answered on another device
                    refresh()
                }
            }
        }
    }

    private fun launchChange(work: suspend () -> Unit) {
        changeJobs.removeAll { !it.isActive }
        changeJobs += scope.launch { work() }
    }

    // also cancels anything in progress so its result can't fill the cleared state
    fun clear() {
        loadJob?.cancel()
        searchJob?.cancel()
        changeJobs.forEach { it.cancel() }
        changeJobs.clear()
        query = ""
        friends = emptyList()
        requests = emptyList()
        searchResults = emptyList()
        isLoading = false
        error = null
        isSearching = false
        searchError = null
        busyRequestIds = emptySet()
        busyUserIds = emptySet()
    }
}
