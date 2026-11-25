package com.example.cpen321application.review.model

// the frame a badge is drawn in, and which unlock animation it plays
enum class BadgeTier { GettingStarted, Milestone, Reputation }

/*  every badge, in the order the profile shows them (three per row). id is what the server
    sends, see backend/src/review/badges.ts. goal is how to earn it, shown in its popup, and
    hint the same in a few words, shown under it on the user's own profile so they know how to get it
 */
enum class Badge(val id: String, val title: String, val goal: String, val hint: String, val tier: BadgeTier) {
    FirstWhistle("first_game", "First Whistle", "Join your first game", "Join a game", BadgeTier.GettingStarted),
    GameMaker("first_host", "Game Maker", "Host your first game", "Host a game", BadgeTier.GettingStarted),
    SquadUp("first_friend", "Squad Up", "Add your first friend", "Add a friend", BadgeTier.GettingStarted),
    FairJudge(
        "first_rating", "Fair Judge", "Rate players after a game for the first time", "Rate your teammates",
        BadgeTier.GettingStarted
    ),
    Regular("games_10", "Regular", "Play 10 games", "Play 10 games", BadgeTier.Milestone),
    Veteran("games_50", "Veteran", "Play 50 games", "Play 50 games", BadgeTier.Milestone),
    SocialButterfly("friends_10", "Social Butterfly", "Have 10 friends", "Have 10 friends", BadgeTier.Milestone),
    Mvp("mvp", "MVP", "Overall score of 5+ after at least 10 ratings", "Score 5+ over 10 ratings", BadgeTier.Reputation),
    MegaMvp("mega_mvp", "Mega MVP", "10 reviews with 6/6 in every category", "10 perfect 6/6 reviews", BadgeTier.Reputation)
}

// the badges with these server ids, in profile order. Ids this version of the app doesn't know are skipped
fun badgesOf(ids: List<String>?): List<Badge> {
    val known = ids.orEmpty().toSet()
    return Badge.entries.filter { it.id in known }
}
