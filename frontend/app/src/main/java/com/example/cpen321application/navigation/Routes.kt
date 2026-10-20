package com.example.cpen321application.navigation

// navigation routes, one per screen
object Routes {
    const val SIGN_IN = "sign_in"
    const val SIGN_UP = "sign_up"
    const val HOME = "home"
    const val CREATE_GAME = "create_game"
    const val PICK_LOCATION = "pick_location"
    const val DISCOVER = "discover"

    // game details takes the game's id, build it with gameDetails(id)
    const val GAME_ID_ARG = "gameId"
    const val GAME_DETAILS = "game_details/{$GAME_ID_ARG}"
    fun gameDetails(gameId: Int) = "game_details/$gameId"

    // the Input Game Info screen filled in with an existing game, build it with editGame(id).
    // CREATE_GAME shows the same screen empty
    const val EDIT_GAME = "edit_game/{$GAME_ID_ARG}"
    fun editGame(gameId: Int) = "edit_game/$gameId"

    // the organizer's pending join requests for a game, build it with joinRequests(id)
    const val JOIN_REQUESTS = "join_requests/{$GAME_ID_ARG}"
    fun joinRequests(gameId: Int) = "join_requests/$gameId"
    const val PROFILE = "profile"

    const val EDIT_PROFILE = "edit_profile"
}

// savedStateHandle key for a message shown on the sign in screen
const val SIGN_IN_MESSAGE_KEY = "sign_in_message"

// savedStateHandle key for the place chosen on the Pick Location screen
const val PICKED_LOCATION_KEY = "picked_location"

// savedStateHandle key for where the Pick Location map starts, when it isn't the picked place
const val PICK_START_KEY = "pick_start"
