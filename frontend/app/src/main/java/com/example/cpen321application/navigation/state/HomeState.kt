package com.example.cpen321application.navigation.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cpen321application.game.model.GameSummary
import com.example.cpen321application.game.model.toGameSummary
import com.example.cpen321application.game.repository.MyGamesResult
import com.example.cpen321application.game.repository.fetchMyGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// the games shown on Home, on failure the last loaded games stay
class HomeState(private val context: Context, private val scope: CoroutineScope) {
    val organizedGames = mutableStateListOf<GameSummary>()
    val joinedGames = mutableStateListOf<GameSummary>()
    var isRefreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            try {
                when (val result = fetchMyGames(context)) {
                    is MyGamesResult.Loaded -> {
                        organizedGames.clear()
                        organizedGames.addAll(result.organized.map { it.toGameSummary() })
                        joinedGames.clear()
                        joinedGames.addAll(result.joined.map { it.toGameSummary() })
                        error = null
                    }
                    is MyGamesResult.Failed -> error = result.message
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun clear() {
        organizedGames.clear()
        joinedGames.clear()
        error = null
    }
}
