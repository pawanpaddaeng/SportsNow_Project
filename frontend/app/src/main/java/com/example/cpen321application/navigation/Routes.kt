package com.example.cpen321application.navigation

// navigation routes, one per screen
object Routes {
    const val SIGN_IN = "sign_in"
    const val SIGN_UP = "sign_up"
    const val HOME = "home"
    const val CREATE_GAME = "create_game"
    const val PICK_LOCATION = "pick_location"

    const val PROFILE = "profile"
}

// savedStateHandle key for a message shown on the sign in screen
const val SIGN_IN_MESSAGE_KEY = "sign_in_message"

// savedStateHandle key for the place chosen on the Pick Location screen
const val PICKED_LOCATION_KEY = "picked_location"
