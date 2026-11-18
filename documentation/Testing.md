# Testing

## 1. Change History

| **Change Date** | **Modified Sections** | **Rationale** |
| --------------- | --------------------- | ------------- |
| Oct 20, 2026 | 2.1, 4 | First test plan for CP1. Section 2.1 plans the back-end tests for the Game component. Section 4 plans the end-to-end test for Use Case 2, Discover Game. We will write and run the tests, and add the results, coverage, GitHub Actions file and logs, at CP2. |
| Nov 17, 2026 | 2, 2.1 | The back-end tests for the Game and Friend components are written. Each interface now has one test file without mocks and one with mocks, so section 2.1.1 gives those files and the line where each `describe` group starts. The test tables now match the tests that were written, and the Friend component's tests are added. Section 2.1.3 explains how to start the test database and run the tests. |
| Nov 17, 2026 | 2, 2.1 | The back-end tests for the Review component are written: its three interfaces, the rating reminder the server checks for every minute, and the score rules. The existing mocked rating tests are split into one file per interface, and each interface gets a test file without mocks. New edge cases cover the 1 to 6 star limits, values that aren't whole numbers, the edges of the 1-day rating window, games that aren't completed, players who didn't show up or left, rating twice or at the same time, the order games count in, and a game exactly 1.5 stars from a player's middle score. The Reputation score case of the join request list now gives the exact score its test checks, 3.86. |
| Nov 18, 2026 | 2, 2.1 | The back-end tests for the Notification component are written (issue #83): its two interfaces, `GET /api/notifications` and `PATCH /api/notifications/read`, and the game start reminder the server checks for every minute, with unit tests for how the reminder says how soon the game starts. The join request, invite and friend request tests now also check the notification each one saves, and the decline friend request tests check that none is. Added an unmocked test for inviting friends successfully. Tests with mocks now replace saving notifications before every test, so they never reach the database. The start reminder tests found a deadlock with other transactions on `games`, fixed by skipping locked games (`FOR UPDATE SKIP LOCKED`). The describe group locations of the changed Game and Friend tests are updated. |
| Nov 18, 2026 | 4.1, 4.2 | Added the front-end tests for the Friend System (`FriendSystemTest.kt`): Search Users, Send and Decline Friend Request, Accept Friend Request, and Send (formal Use Case 1), Receive and View Friend Messages. Section 4.2 explains how the manual tester acts as User B with `scripts/run-e2e-user-b`, and how to run the tests on User A's emulator only. Logs will be added once the tests pass. |

---

## 2. Back-end Test Specification: APIs

We test every interface of the **Game**, **Friend**, **Review** and **Notification** components that is built (see Requirements and Design, section 4.1). The Review and Game components also have timed parts the app never calls, the rating reminder and the game start reminder the server checks for every minute, which are tested the same way. The test folders are named after the back-end components in `backend/src`, so the Game tests are in `game`, the Friend tests in `friend`, the Review tests in `review` and the Notification tests in `notification`.

Each interface has two test files, each with one `describe` group:
- one **without mocks**, in `backend/tests/no-mock/`, for example `describe('Unmocked: GET /api/games/discover', ...)`
- one **with mocks**, in `backend/tests/mock/`, for example `describe('Mocked: GET /api/games/discover', ...)`

Keeping the two kinds in separate folders lets us run each one on its own.

Each test has comments that say its input, the status code we expect, what should happen, and what it should return. Tests with mocks also say what the mock does.

**Tests without mocks** use a real MySQL database:
- Before each test, we add the users the test needs, and each test adds the games, requests and friendships it needs.
- After each test, we delete the users it added. Deleting a user also deletes their games, players, chats, join requests, friend requests and friendships, so one test can't change another.
- We sign in by making a JWT for the test user with `createAuthToken`. This way the tests don't need Google sign in.
- The Friend tests search all users, and other test files run at the same time on the same database. So the search tests use made-up names that no other test uses, and only check the users they added.
- The Review tests mark a game completed with the same `completeGame` the organizer's request uses, then move the time it was completed back to test the 1-day rating window. To see who is sent a rating reminder, they watch `sendPushToUsers` with `jest.spyOn` without replacing it. The test users have no devices, so no notification leaves the server. Other test files' games can be due for a reminder at the same time, so these tests only check their own games.
- The tests of routes that send a notification check the row saved in `notifications`. The route replies before saving it, so the test waits for it.

**Tests with mocks** don't use the database. They replace the database settings with `backend/tests/mock/mockEnv.ts`, and sign in by mocking the user lookup. They use `jest.spyOn` to:
- make database functions throw an error, to check that the server answers `500 server_error`
- replace the push notification functions (`sendPushToUser` and `sendPushToUsers`), to check who gets a notification and what type it is
- replace `saveNotifications`, to check which notifications are saved for the Notifications screen. It is replaced before every test with mocks (in `cleanUpAfterTests`), so no test with mocks tries to reach the database
- make a push notification fail, to check the server still answers and logs the failure
- replace the database transaction with a fake one, to test a request that is deleted while the server waits for it
- The rating reminder's tests with mocks also use Jest's fake timers, so the server's check every minute can run without waiting.

### 2.1. Locations of Back-end Tests and Instructions to Run Them

#### 2.1.1. Tests

**Game component**

| **Interface** | **Describe Group Location, No Mocks** | **Describe Group Location, With Mocks** | **Mocked Components** |
| ------------- | ------------------------------------- | --------------------------------------- | --------------------- |
| **POST /api/games** | [`backend/tests/no-mock/game/createGame.test.ts#L18`](../backend/tests/no-mock/game/createGame.test.ts#L18) | [`backend/tests/mock/game/createGame.test.ts#L10`](../backend/tests/mock/game/createGame.test.ts#L10) | Games DB |
| **GET /api/games/mine** | [`backend/tests/no-mock/game/getMyGames.test.ts#L14`](../backend/tests/no-mock/game/getMyGames.test.ts#L14) | [`backend/tests/mock/game/getMyGames.test.ts#L10`](../backend/tests/mock/game/getMyGames.test.ts#L10) | Games DB |
| **GET /api/games/discover** | [`backend/tests/no-mock/game/discoverGames.test.ts#L41`](../backend/tests/no-mock/game/discoverGames.test.ts#L41) | [`backend/tests/mock/game/discoverGames.test.ts#L13`](../backend/tests/mock/game/discoverGames.test.ts#L13) | Games DB |
| **GET /api/games/{gameId}** | [`backend/tests/no-mock/game/getGameDetails.test.ts#L27`](../backend/tests/no-mock/game/getGameDetails.test.ts#L27) | [`backend/tests/mock/game/getGameDetails.test.ts#L21`](../backend/tests/mock/game/getGameDetails.test.ts#L21) | Games DB, Join Requests DB, MySQL queries |
| **PUT /api/games/{gameId}** | [`backend/tests/no-mock/game/updateGame.test.ts#L29`](../backend/tests/no-mock/game/updateGame.test.ts#L29) | [`backend/tests/mock/game/updateGame.test.ts#L22`](../backend/tests/mock/game/updateGame.test.ts#L22) | Games DB, Firebase push |
| **POST /api/games/{gameId}/cancel** | [`backend/tests/no-mock/game/cancelGame.test.ts#L25`](../backend/tests/no-mock/game/cancelGame.test.ts#L25) | [`backend/tests/mock/game/cancelGame.test.ts#L20`](../backend/tests/mock/game/cancelGame.test.ts#L20) | Games DB, Firebase push |
| **POST /api/games/{gameId}/complete** | [`backend/tests/no-mock/game/completeGame.test.ts#L13`](../backend/tests/no-mock/game/completeGame.test.ts#L13) | [`backend/tests/mock/game/completeGame.test.ts#L10`](../backend/tests/mock/game/completeGame.test.ts#L10) | Games DB |
| **POST /api/games/{gameId}/leave** | [`backend/tests/no-mock/game/leaveGame.test.ts#L18`](../backend/tests/no-mock/game/leaveGame.test.ts#L18) | [`backend/tests/mock/game/leaveGame.test.ts#L10`](../backend/tests/mock/game/leaveGame.test.ts#L10) | Games DB |
| **DELETE /api/games/{gameId}/players/{userId}** | [`backend/tests/no-mock/game/removePlayer.test.ts#L14`](../backend/tests/no-mock/game/removePlayer.test.ts#L14) | None yet | None |
| **POST /api/games/{gameId}/invites** | [`backend/tests/no-mock/game/inviteFriends.test.ts#L32`](../backend/tests/no-mock/game/inviteFriends.test.ts#L32) | [`backend/tests/mock/game/inviteFriends.test.ts#L25`](../backend/tests/mock/game/inviteFriends.test.ts#L25) | Games DB, Friends DB, Notifications DB, Firebase push |
| **POST /api/games/{gameId}/join-requests** | [`backend/tests/no-mock/game/sendJoinRequest.test.ts#L32`](../backend/tests/no-mock/game/sendJoinRequest.test.ts#L32) | [`backend/tests/mock/game/sendJoinRequest.test.ts#L24`](../backend/tests/mock/game/sendJoinRequest.test.ts#L24) | Join Requests DB, Notifications DB, Firebase push |
| **GET /api/games/{gameId}/join-requests** | [`backend/tests/no-mock/game/listJoinRequests.test.ts#L34`](../backend/tests/no-mock/game/listJoinRequests.test.ts#L34) | [`backend/tests/mock/game/listJoinRequests.test.ts#L11`](../backend/tests/mock/game/listJoinRequests.test.ts#L11) | Games DB, Join Requests DB |
| **PATCH /api/games/{gameId}/join-requests/{requestId}** | [`backend/tests/no-mock/game/answerJoinRequest.test.ts#L33`](../backend/tests/no-mock/game/answerJoinRequest.test.ts#L33) | [`backend/tests/mock/game/answerJoinRequest.test.ts#L28`](../backend/tests/mock/game/answerJoinRequest.test.ts#L28) | Games DB, Join Requests DB, Notifications DB, Firebase push |
| **Game start reminders** (the server's check every minute) | [`backend/tests/no-mock/game/startReminders.test.ts#L60`](../backend/tests/no-mock/game/startReminders.test.ts#L60) | [`backend/tests/mock/game/startReminders.test.ts#L21`](../backend/tests/mock/game/startReminders.test.ts#L21) | Start Reminders DB, Notifications DB, Firebase push, Logger, timers |

How a start reminder says how soon the game starts has no database code, so it also has unit tests, in [`backend/tests/unit/game/timeUntilStart.test.ts#L4`](../backend/tests/unit/game/timeUntilStart.test.ts#L4).

**Friend component**

| **Interface** | **Describe Group Location, No Mocks** | **Describe Group Location, With Mocks** | **Mocked Components** |
| ------------- | ------------------------------------- | --------------------------------------- | --------------------- |
| **GET /api/users/search** | [`backend/tests/no-mock/friend/searchUsers.test.ts#L40`](../backend/tests/no-mock/friend/searchUsers.test.ts#L40) | [`backend/tests/mock/friend/searchUsers.test.ts#L10`](../backend/tests/mock/friend/searchUsers.test.ts#L10) | Friends DB |
| **GET /api/friends** | [`backend/tests/no-mock/friend/getFriends.test.ts#L13`](../backend/tests/no-mock/friend/getFriends.test.ts#L13) | [`backend/tests/mock/friend/getFriends.test.ts#L10`](../backend/tests/mock/friend/getFriends.test.ts#L10) | Friends DB |
| **GET /api/friends/requests** | [`backend/tests/no-mock/friend/listFriendRequests.test.ts#L13`](../backend/tests/no-mock/friend/listFriendRequests.test.ts#L13) | [`backend/tests/mock/friend/listFriendRequests.test.ts#L10`](../backend/tests/mock/friend/listFriendRequests.test.ts#L10) | Friends DB |
| **POST /api/friends/requests** | [`backend/tests/no-mock/friend/sendFriendRequest.test.ts#L15`](../backend/tests/no-mock/friend/sendFriendRequest.test.ts#L15) | [`backend/tests/mock/friend/sendFriendRequest.test.ts#L22`](../backend/tests/mock/friend/sendFriendRequest.test.ts#L22) | Friends DB, Firebase push, Logger |
| **PATCH /api/friends/requests/{requestId}** | [`backend/tests/no-mock/friend/answerFriendRequest.test.ts#L25`](../backend/tests/no-mock/friend/answerFriendRequest.test.ts#L25) | [`backend/tests/mock/friend/answerFriendRequest.test.ts#L24`](../backend/tests/mock/friend/answerFriendRequest.test.ts#L24) | Friends DB, MySQL transaction, Notifications DB, Firebase push, Logger |

The unmocked Friend tests share their setup helpers in [`backend/tests/no-mock/friend/friendHelpers.ts`](../backend/tests/no-mock/friend/friendHelpers.ts).

**Review component**

| **Interface** | **Describe Group Location, No Mocks** | **Describe Group Location, With Mocks** | **Mocked Components** |
| ------------- | ------------------------------------- | --------------------------------------- | --------------------- |
| **GET /api/games/{gameId}/ratings** | [`backend/tests/no-mock/review/listPlayersToRate.test.ts#L54`](../backend/tests/no-mock/review/listPlayersToRate.test.ts#L54) | [`backend/tests/mock/review/listPlayersToRate.test.ts#L13`](../backend/tests/mock/review/listPlayersToRate.test.ts#L13) | Games DB, Ratings DB |
| **POST /api/games/{gameId}/ratings** | [`backend/tests/no-mock/review/ratePlayer.test.ts#L40`](../backend/tests/no-mock/review/ratePlayer.test.ts#L40) | [`backend/tests/mock/review/ratePlayer.test.ts#L27`](../backend/tests/mock/review/ratePlayer.test.ts#L27) | Ratings DB |
| **GET /api/users/me/stats** | [`backend/tests/no-mock/review/getMyStats.test.ts#L25`](../backend/tests/no-mock/review/getMyStats.test.ts#L25) | [`backend/tests/mock/review/getMyStats.test.ts#L13`](../backend/tests/mock/review/getMyStats.test.ts#L13) | Reputation DB |
| **Rating reminders** (the server's check every minute) | [`backend/tests/no-mock/review/ratingReminders.test.ts#L44`](../backend/tests/no-mock/review/ratingReminders.test.ts#L44) | [`backend/tests/mock/review/ratingReminders.test.ts#L20`](../backend/tests/mock/review/ratingReminders.test.ts#L20) | Rating Reminders DB, Firebase push, Logger, timers |

The unmocked Review tests share their setup helpers in [`backend/tests/no-mock/review/reviewHelpers.ts`](../backend/tests/no-mock/review/reviewHelpers.ts). The rules that work out a reputation score have no database code, so they also have unit tests, in [`backend/tests/unit/review/reputation.test.ts#L22`](../backend/tests/unit/review/reputation.test.ts#L22).

**Notification component** (the shared Notifications module, Requirements and Design section 4.1)

| **Interface** | **Describe Group Location, No Mocks** | **Describe Group Location, With Mocks** | **Mocked Components** |
| ------------- | ------------------------------------- | --------------------------------------- | --------------------- |
| **GET /api/notifications** | [`backend/tests/no-mock/notification/listNotifications.test.ts#L19`](../backend/tests/no-mock/notification/listNotifications.test.ts#L19) | [`backend/tests/mock/notification/listNotifications.test.ts#L36`](../backend/tests/mock/notification/listNotifications.test.ts#L36) | Notifications DB, Friends DB |
| **PATCH /api/notifications/read** | [`backend/tests/no-mock/notification/markNotificationsRead.test.ts#L16`](../backend/tests/no-mock/notification/markNotificationsRead.test.ts#L16) | [`backend/tests/mock/notification/markNotificationsRead.test.ts#L10`](../backend/tests/mock/notification/markNotificationsRead.test.ts#L10) | Notifications DB |

The unmocked Notification tests share their setup helpers in [`backend/tests/no-mock/notification/notificationHelpers.ts`](../backend/tests/no-mock/notification/notificationHelpers.ts). The Game and Friend tests use them too, to check the notifications their routes save. `notifyUser` and `notifyUsers` are tested through the routes and reminders that call them.

#### Test cases

**Signing in**

Every route checks that the user is signed in first, in the same middleware. The Friend, Review and Notification components' groups without mocks test it for each of their interfaces:

| **Test** | **Input** | **Expected status** | **What should happen** |
| -------- | --------- | ------------------- | ---------------------- |
| Rejects a call that is not signed in | No `Authorization` header, then `Authorization: Bearer not-a-real-token` | 400, then 401 | Nothing changes. Returns `{ msg: "invalid_request" }`, then `{ msg: "invalid_token", error: "Invalid jwt_token" }` |

##### Game component

**POST /api/games** (create a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Organizer creates a game | All fields, starting tomorrow | 201 | The game is saved. The user is its organizer and first player, and its chat is opened. Returns the game with `player_count` 1 and `status` "scheduled" |
| With mocks | Database fails while saving | `createGame` throws | 500 | Returns `{ msg: "server_error" }`. The game is never read back |
| With mocks | Invalid body never reaches the database | A game without `title`, `createGame` watched | 400 | Returns `{ msg: "invalid_request" }`. `createGame` is never called |

**GET /api/games/mine** (the user's games)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Organized and joined | The user organizes one game and plays in another | 200 | `organized_games` and `joined_games` each have the right game |
| No mocks | No games | A user with no games | 200 | Returns `{ msg: "no_games" }` |
| With mocks | Database fails | `getGamesForUser` throws | 500 | Returns `{ msg: "server_error" }` |

**GET /api/games/discover** (games near the user)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Nearby games, closest first | Three games by other users, 200 m, 1 km and 2 km away | 200 | Returns the three games in that order. Each has `distance_m`, and `join_request_status` is null |
| No mocks | Full game still shows | A nearby game with no spots left | 200 | The game is in the list, with `player_count` equal to `max_players` |
| No mocks | Games that should not show | The user's own game, a game they joined, a cancelled game, a past game, and a game 60 km away | 200 | Returns `{ msg: "no_games" }` |
| No mocks | Request status | The user has a pending request for a nearby game | 200 | That game has `join_request_status: "pending"` |
| No mocks | Location out of range | `lat=95&lng=0` | 400 | Returns `{ msg: "invalid_request" }` |
| No mocks | No location | No `lat` in the query | 400 | Returns `{ msg: "invalid_request" }` |
| With mocks | Database fails | `getDiscoverGames` throws | 500 | Returns `{ msg: "server_error" }` |

**GET /api/games/{gameId}** (game details)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Organizer | The organizer opens their game, which has two pending requests | 200 | `viewer_role` is "organizer" and `pending_requests` is 2 |
| No mocks | Player | A user opens a game they joined | 200 | `viewer_role` is "player" |
| No mocks | Viewer | A user opens a game they asked to join | 200 | `viewer_role` is "viewer" and `join_request_status` is "pending" |
| No mocks | Bad id | `/api/games/abc` | 400 | Returns `{ msg: "invalid_request" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| With mocks | Organizer not in players list | `getGamePlayers` doesn't return the organizer, the user lookup in `findOrganizer` does | 200 | `organizer` comes from the user lookup |
| With mocks | Organizer not found | Neither lookup finds the organizer | 500 | Returns `{ msg: "server_error" }` |
| With mocks | Database fails | `getGameById` throws | 500 | Returns `{ msg: "server_error" }` |

**PUT /api/games/{gameId}** (edit a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Valid edit | The organizer changes the title and time | 200 | The game is updated. Returns the game with the new values |
| No mocks | Bad field | `sport: "chess"` | 400 | The game doesn't change. Returns `{ msg: "invalid_request" }` |
| No mocks | Too few spots | `max_players` is less than the players already in the game | 400 | The game doesn't change. The error says how many players are in it |
| No mocks | Cancelled game | Edit a cancelled game | 409 | The game doesn't change. Returns `{ msg: "game_cancelled" }` |
| No mocks | Game already started | Edit a game that already started | 409 | The game doesn't change. Returns `{ msg: "game_started" }` |
| No mocks | Not the organizer | Another user edits the game | 403 | The game doesn't change. Returns `{ msg: "forbidden" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| With mocks | Players are told | Valid edit, `sendPushToUsers` replaced | 200 | Players and people with pending requests (not the organizer) get a `game_updated` notification that says what changed |
| With mocks | Nothing changed | Same values as before, `sendPushToUsers` replaced | 200 | No notification is sent |
| With mocks | Database fails | `updateGame` throws | 500 | Returns `{ msg: "server_error" }` |

**POST /api/games/{gameId}/cancel** (cancel a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Organizer cancels their game | The organizer cancels an upcoming game | 200 | The game's `status` becomes "cancelled". Returns the game |
| No mocks | Already cancelled | Cancel the same game twice | 409 | Returns `{ msg: "game_cancelled" }` the second time |
| No mocks | Game already started | Cancel a game that already started | 409 | The game doesn't change. Returns `{ msg: "game_started" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| No mocks | Not the organizer | Another user cancels it | 403 | The game doesn't change. Returns `{ msg: "forbidden" }` |
| With mocks | Players are told | `sendPushToUsers` replaced | 200 | Players and people with pending requests get a `game_cancelled` notification |
| With mocks | Database fails | `cancelGame` throws | 500 | Returns `{ msg: "server_error" }` |

**POST /api/games/{gameId}/complete** (mark a game completed)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Organizer marks their started game completed | The organizer completes their started game | 200 | The game's `status` becomes "completed". Returns the game |
| No mocks | A malformed no-show list is rejected | `no_show_user_ids: ["seven"]` | 400 | The game doesn't change. Returns `{ msg: "invalid_request" }` |
| With mocks | Organizer marks players who did not show up | `no_show_user_ids: [7, 8, 7]` | 200 | `completeGame` gets each no-show once, `[7, 8]` |

**POST /api/games/{gameId}/leave** (leave a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Player leaves a game that has another player | A player leaves an upcoming game that has another player | 200 | The player is no longer in the game, and the other player and the game remain. Returns the game with `player_count` down by one |
| No mocks | Last player leaves and the game is deleted | The organizer, the only player left in their cancelled game, leaves it | 200 | The game, its players and its chat are deleted. Returns `{ msg: "success", game: null, game_deleted: true }` |
| With mocks | Database fails | `leaveGame` throws | 500 | Returns `{ msg: "server_error" }` |

**DELETE /api/games/{gameId}/players/{userId}** (remove a player from a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Organizer removes the second player | The organizer removes the other player from their upcoming game | 200 | The player is no longer in the game, and only the organizer is left. Returns the game with `player_count` 1 |

**POST /api/games/{gameId}/invites** (invite friends to a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Organizer invites two friends | The organizer invites two friends who aren't in their upcoming game | 201 | Each friend has a saved `game_invite` notification, "Olivia invited you to Evening Basketball", for the game. Returns `{ msg: "success" }` |
| No mocks | Organizer cannot invite to a cancelled, started, completed or full game | The organizer invites their friend to a game that isn't open | 409 | Returns `{ msg: "game_cancelled" }`, `{ msg: "game_started" }` (started or completed) or `{ msg: "game_full" }` |
| No mocks | Organizer cannot invite a friend already in the game | The organizer invites a friend and a friend who is already playing | 409 | Returns `{ msg: "already_player" }` |
| No mocks | `friend_ids` is rejected | `friend_ids` is missing, empty, text, not whole or zero | 400 | Returns `{ msg: "invalid_request" }` |
| No mocks | Organizer cannot invite someone who is not their friend | The organizer invites a friend and a user who isn't their friend | 403 | Nobody is invited and no notification is saved. Returns `{ msg: "not_friend" }` |
| No mocks | A player who is not the organizer cannot invite | A player invites their own friend | 403 | Returns `{ msg: "forbidden" }` |
| With mocks | Organizer invites two friends | The organizer invites two friends, `saveNotifications` and `sendPushToUser` replaced | 201 | Each friend gets a `game_invite` notification, saved and sent as a push. Returns `{ msg: "success" }` |
| With mocks | Database error | `getGameById` throws | 500 | Nobody is notified. Returns `{ msg: "server_error" }` |

**POST /api/games/{gameId}/join-requests** (ask to join a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Valid request | `skill_level`, `play_frequency` and `message` for another user's game | 201 | A pending request is saved with the answers, and the organizer has a saved `join_request` notification, "Rae wants to join Evening Basketball", for the game |
| No mocks | Only skill level | `{ skill_level: "beginner" }` | 201 | Saved, with `play_frequency` and `message` empty |
| No mocks | Ask again | The user's earlier request was declined | 201 | The same request goes back to pending, with the new answers |
| No mocks | Bad answers | No `skill_level`; an unknown `play_frequency`; a message of 201 characters | 400 | Nothing is saved. The error names the bad field |
| No mocks | Already asked | A second request while the first is still pending | 409 | Returns `{ msg: "already_requested" }` |
| No mocks | Already a player | The user is already in the game | 409 | Returns `{ msg: "already_player" }` |
| No mocks | Asking to join a cancelled or started game | A cancelled game, and a game that already started | 409 | Returns `{ msg: "game_cancelled" }` and `{ msg: "game_started" }` |
| No mocks | Game is full | No spots left | 409 | Returns `{ msg: "game_full" }` |
| No mocks | Own game | The organizer asks to join their own game | 409 | Nothing is saved. Returns `{ msg: "own_game" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| With mocks | Organizer is told | Valid request, `saveNotifications` and `sendPushToUser` replaced | 201 | The organizer gets a `join_request` notification with the game's id, saved and sent as a push |
| With mocks | Database fails | `createJoinRequest` throws | 500 | Returns `{ msg: "server_error" }` |

**GET /api/games/{gameId}/join-requests** (the organizer's list of requests)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Pending requests | The organizer's game has two pending requests and one declined | 200 | Returns only the two pending requests, newest first, with each person's name and answers |
| No mocks | Reputation score | The requester has a no-show in one past game and 3 stars from the only other player in another | 200 | The request's `reputation_score` is 3.86 |
| No mocks | No requests | Nobody has asked to join | 200 | `requests` is an empty list |
| No mocks | Not the organizer | Another user asks for the list | 403 | Returns `{ msg: "forbidden" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| With mocks | Database fails | `getPendingJoinRequests` throws | 500 | Returns `{ msg: "server_error" }` |

**PATCH /api/games/{gameId}/join-requests/{requestId}** (approve or decline a request)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Game is full | Approve when no spots are left | 409 | Nothing changes. Returns `{ msg: "game_full" }` |
| No mocks | Approve | `{ decision: "approve" }` for a pending request | 200 | The request is approved and the person joins the game. `player_count` goes up by one. The person has a saved `join_request_approved` notification for the game |
| No mocks | Decline | `{ decision: "decline" }` | 200 | The request is declined. The players don't change. The person has a saved `join_request_declined` notification for the game |
| No mocks | Already answered | Answer the same request twice | 409 | Returns `{ msg: "already_responded" }` the second time |
| No mocks | Bad input | `{ decision: "maybe" }`, and a bad request id | 400 | The request stays pending. Returns `{ msg: "invalid_request" }` |
| No mocks | Cancelled game | Answer a request for a cancelled game | 409 | Returns `{ msg: "game_cancelled" }` |
| No mocks | Not the organizer | Another user answers | 403 | Nothing changes. Returns `{ msg: "forbidden" }` |
| No mocks | Request doesn't exist | A request id that isn't in this game | 404 | Returns `{ msg: "request_not_found" }` |
| With mocks | Person is told | Approve one request and decline another, `saveNotifications` and `sendPushToUser` replaced | 200 | The first person gets `join_request_approved` and the second gets `join_request_declined`, each saved and sent as a push |
| With mocks | Database fails | `decideJoinRequest` throws | 500 | Returns `{ msg: "server_error" }` |

**Game start reminders** (checked by the server every minute, not called by the app)

The tests without mocks start each game 30 seconds past the minute they name (e.g. 30 minutes 30 seconds), so the whole minutes left don't change however long the test takes to reach the check. Other test files' games can be due at the same time, so these tests only check their own games.

| **Group** | **Test** | **Input** | **What should happen** |
| --------- | -------- | --------- | ---------------------- |
| No mocks | The organizer and players are reminded once | A game starting in 30 minutes with the organizer and Pete. The check runs twice | The organizer and Pete each have one saved `game_starting` notification, "Evening Basketball starts in 30 minutes". The game is marked reminded, so the second check sends nothing |
| No mocks | A reminder on time says 1 hour | A game starting in 59 minutes | The reminder says "Evening Basketball starts in 1 hour" |
| No mocks | Games not starting within the hour, started or cancelled are not reminded | A game starting in 2 hours, one that started yesterday, and a cancelled one starting in 30 minutes, each with Pete | Nobody is reminded, and none of the games is marked reminded |
| No mocks | Moving the start time reminds the players again | A reminded game. The organizer edits only its title with `PUT /api/games/{gameId}`, then moves it to start in 2 hours, then in 45 minutes | The title edit keeps it reminded. Moving it to 2 hours clears it and nothing is sent. Moving it to 45 minutes sends "New Title starts in 45 minutes" |
| With mocks | Everyone in each due game is reminded | `claimDueStartReminders` returns games starting in 60 and 25 minutes, `getGamePlayerIds`, `saveNotifications` and `sendPushToUser` replaced | Each game's players get a `game_starting` notification, saved and sent as a push: "Sunday Soccer starts in 1 hour" and "Monday Hoops starts in 25 minutes". Returns 2 |
| With mocks | Nothing is sent when no reminder is due | `claimDueStartReminders` returns no games | Nothing is saved or sent. Returns 0 |
| With mocks | A due game with nobody in it sends nothing | `claimDueStartReminders` returns a game, `getGamePlayerIds` returns nobody | Nothing is saved or sent. Returns 1 |
| With mocks | A failed save is logged and the push is still sent | `saveNotifications` throws | The failure is logged as "Saving a game_starting notification failed:", and both players still get the push |
| With mocks | The server checks every minute until it is stopped | Fake timers: two minutes pass, the reminders stop, two more minutes pass | 2 checks, none after stopping |
| With mocks | A failed check is logged and the next one still runs | Fake timers: `claimDueStartReminders` throws once, then two minutes pass | The failure is logged as "Sending game start reminders failed:", and the next check still runs |

**How soon a game starts** (unit tests of `timeUntilStart` in `backend/src/game/startReminders.ts`, no database)

| **Test** | **Input** (whole minutes left) | **Expected output** |
| -------- | ------------------------------ | ------------------- |
| A reminder sent on time reads 1 hour | 60, then 55 | "1 hour" |
| A later reminder gives the minutes left | 54, then 2 | "54 minutes", then "2 minutes" |
| One minute left is singular | 1 | "1 minute" |

##### Friend component

**GET /api/users/search** (search for users by name)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | Nobody is searched for |
| No mocks | Bad query | `q` missing, empty, only spaces, 101 characters, or given twice | 400 | Returns `{ msg: "invalid_request", error: "q must be text of 1 to 100 characters" }` |
| No mocks | Accepts a query of exactly 100 characters | `q` of 100 characters that matches nobody | 200 | Returns `{ msg: "success", users: [] }` |
| No mocks | No user matches | `q` that no name is close to | 200 | Returns `{ msg: "success", users: [] }` |
| No mocks | The searcher is left out of their own results | The searcher searches their own full name, with spaces around it | 200 | The text is trimmed. Another user with the same name is found, the searcher is not |
| No mocks | Each result shows its friendship status | Matching users: a friend, one the searcher asked, one who asked the searcher, one whose request the searcher declined, and a stranger | 200 | `friendship_status` is `friends`, `request_sent`, `request_received`, and `none` for the stranger and for a declined request |
| No mocks | Best matches come first | Names that are the same, start with `q`, have a word starting with `q`, contain `q`, or are one or two letters off | 200 | Results come in that order, equal matches by name then id. Names that are too different are left out |
| No mocks | Returns at most 20 users | 21 users match | 200 | The first 20 by name are returned |
| With mocks | Database fails | `searchUsers` throws | 500 | Returns `{ msg: "server_error" }`. `searchUsers` got the trimmed text |

**GET /api/friends** (the user's friends)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No friends are read |
| No mocks | User has no friends | A user with no friends | 200 | Returns `{ msg: "success", friends: [] }` |
| No mocks | Friends are listed in name order with their chat | Three friends, pending requests each way, and a stranger | 200 | Only the friends are listed, by first name, each with the `chat_id` the two share |
| No mocks | Both friends see each other with the same chat | Two friends, the second lists their friends | 200 | The first user is listed with the same `chat_id` |
| With mocks | Database fails | `getFriends` throws | 500 | Returns `{ msg: "server_error" }` |

**GET /api/friends/requests** (friend requests waiting for the user's answer)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No requests are read |
| No mocks | User has no requests | Nobody sent the user a request | 200 | Returns `{ msg: "success", requests: [] }` |
| No mocks | Pending requests are listed newest first | Four pending requests, two sent at the same time | 200 | Newest first, and requests sent at the same time newest id first. Each has `request_id` and the sender's name |
| No mocks | Only pending requests to the user are listed | Accepted and declined requests, a request the user sent, a request between two other users, and one pending request to the user | 200 | Only the pending request to the user is listed |
| With mocks | Database fails | `getIncomingFriendRequests` throws | 500 | Returns `{ msg: "server_error" }` |

**POST /api/friends/requests** (send a friend request)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No request is saved |
| No mocks | Bad user_id | No body, no `user_id`, or a `user_id` that is text, 0, negative, a decimal, or too big | 400 | No request is saved. Returns `{ msg: "invalid_request" }` |
| No mocks | Cannot send a request to yourself | The user's own `user_id` | 409 | No request is saved. Returns `{ msg: "own_request" }` |
| No mocks | User does not exist | A `user_id` no user has | 404 | No request is saved. Returns `{ msg: "user_not_found" }` |
| No mocks | Already friends | A request to a friend | 409 | No request is saved. Returns `{ msg: "already_friends" }` |
| No mocks | Already sent a request | A second request while the first is pending | 409 | The first request stays the only one. Returns `{ msg: "already_requested" }` |
| No mocks | Already received a request from them | The other user already has a pending request to the user | 409 | Theirs stays pending. Returns `{ msg: "request_received" }` |
| No mocks | Sends a request | A user with no friendship or request with the sender | 201 | A pending request is saved. Returns `{ request: { request_id, user_id, status: "pending" } }` |
| No mocks | Asks again after being declined | The other user declined the sender's earlier request | 201 | The earlier request is pending again, no second one is saved. Returns the earlier `request_id` |
| With mocks | Database fails | `sendFriendRequest` throws | 500 | Nobody is notified. Returns `{ msg: "server_error" }` |
| With mocks | Receiver is told | A request that saves, `sendPushToUser` replaced | 201 | The receiver gets a `friend_request` notification naming the sender |
| With mocks | Notification fails | A request that saves, `sendPushToUser` throws | 201 | The request is still sent and the failed notification is logged |

**PATCH /api/friends/requests/{requestId}** (accept or decline a friend request)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | The request stays pending |
| No mocks | Bad input | A `requestId` of "abc" or 0, no `decision`, or `decision: "maybe"` | 400 | The request stays pending. Returns `{ msg: "invalid_request" }` |
| No mocks | Request does not exist | A request id no request has | 404 | Returns `{ msg: "request_not_found" }` |
| No mocks | Only the receiver can answer | The sender and a third user each try to accept | 404 | The request stays pending and nobody becomes friends. Returns `{ msg: "request_not_found" }` |
| No mocks | Receiver accepts | `{ decision: "accept" }` | 200 | The request is accepted, the two become friends with one shared chat, and each sees the other in `GET /api/friends`. The sender has a saved `friend_request_accepted` notification |
| No mocks | Receiver declines | `{ decision: "decline" }` | 200 | The request is declined. They don't become friends, no chat is opened, and no notification is saved for the sender |
| No mocks | A request can only be answered once | Answer an accepted request again, with "decline" and with "accept" | 409 | The first answer stands. Returns `{ msg: "already_responded" }` |
| With mocks | Database fails | `decideFriendRequest` throws | 500 | Nobody is notified. Returns `{ msg: "server_error" }` |
| With mocks | Sender is told when the receiver accepts | "accept", `saveNotifications` and `sendPushToUser` replaced | 200 | The sender gets a `friend_request_accepted` notification, saved and sent as a push |
| With mocks | Sender is not told when the receiver declines | "decline", `saveNotifications` and `sendPushToUser` replaced | 200 | Nothing is saved or sent |
| With mocks | Notification fails | An answer that saves, `sendPushToUser` throws | 200 | The answer is still saved and the failed notification is logged |
| With mocks | Request is gone once locked | A fake transaction: the request is found, then gone after the users are locked | 404 | Nothing is written and nobody is notified. Returns `{ msg: "request_not_found" }` |


##### Review component

The tests without mocks use a completed game with the organizer (Olivia) and players Alice, Ben and Cara, where Ned didn't show up and Sam was never in the game. Rating is open for 1 day after the organizer marks the game completed (Requirements and Design, Use Case 2: Rate Players).

**GET /api/games/{gameId}/ratings** (the players the user can rate after a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No players are read |
| No mocks | Lists the other players who showed up and who was rated | Alice opens the list. She already rated Ben | 200 | Returns `game_title`, `organizer_id`, and Ben, Cara and Olivia in name order, Ben with `rated: true`. Alice and Ned are left out, and no stars are sent |
| No mocks | Nobody else showed up | The organizer opens the list when the only other player didn't show up | 200 | `players` is an empty list |
| No mocks | A player who left after the game was completed still counts | Ben leaves the completed game, then Ben and Alice open the list | 200 | Ben still gets his list, and Alice's list still has Ben |
| No mocks | Rejects a bad game id | Game ids `abc`, `0` and `1.5` | 400 | Returns `{ msg: "invalid_request", error: "gameId must be a positive whole number" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| No mocks | Cannot open the list of a game that isn't completed | A player opens an upcoming, a started and a cancelled game | 403 | Returns `{ msg: "forbidden" }` |
| No mocks | Only players who showed up can open the list | Ned, who didn't show up, and Sam, who wasn't in the game | 403 | Returns `{ msg: "forbidden" }` |
| No mocks | The list is still open just before a day has passed | Opened 23 hours 59 minutes after the game was completed | 200 | Returns the players |
| No mocks | The list closes once a day has passed | Opened 24 hours 1 minute after the game was completed | 403 | The players aren't shown. Returns `{ msg: "rating_closed", error: "Rating for this game has closed" }` |
| With mocks | Lists the players to rate without any scores | `getGameById` and `getPlayersToRate` replaced | 200 | Returns the players and who was rated, with no stars anywhere in the reply |
| With mocks | A no-show cannot open the list | `getPlayersToRate` says `not_allowed` | 403 | Returns `{ msg: "forbidden" }` |
| With mocks | The list closes a day after the game is completed | `getPlayersToRate` says `rating_closed` | 403 | Returns `{ msg: "rating_closed", error: "Rating for this game has closed" }` |
| With mocks | Database fails while reading the game | `getGameById` throws | 500 | Returns `{ msg: "server_error" }`. The players are never read |
| With mocks | Database fails while reading the players | `getPlayersToRate` throws | 500 | Returns `{ msg: "server_error", error: "Could not load the players, try again later" }` |

**POST /api/games/{gameId}/ratings** (rate a player)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No rating is saved |
| No mocks | Records a rating | Alice rates Ben 6, 5, 4, 3 and 2 stars | 201 | The rating is saved with Alice as the rater and each category's stars. Returns `{ msg: "success" }` |
| No mocks | Accepts the lowest and highest stars | 1 star, then 6 stars, in every category | 201 | The rating is saved with those stars |
| No mocks | Rejects stars outside 1 to 6 | `skill: 0`, then `skill: 7` | 400 | Nothing is saved. Returns `{ msg: "invalid_request", error: "skill must be a whole number from 1 to 6" }` |
| No mocks | Rejects stars that aren't whole numbers | `overall` of `3.5`, `"5"`, `null` and `true` | 400 | Nothing is saved. Returns `{ msg: "invalid_request", error: "overall must be a whole number from 1 to 6" }` |
| No mocks | Names every missing category | Only `punctuality` is given | 400 | Nothing is saved. The error names sportsmanship, skill, reliability and overall |
| No mocks | Rejects a missing or invalid rated_user_id | No body, then `rated_user_id` left out, `"2"`, `0`, `-1` and `1.5` | 400 | Nothing is saved. Returns `{ msg: "invalid_request", error: "rated_user_id must be a user id" }` |
| No mocks | Rejects a bad game id | `/api/games/abc/ratings` | 400 | Returns `{ msg: "invalid_request" }` |
| No mocks | Game doesn't exist | An id with no game | 403 | Nothing is saved. Returns `{ msg: "forbidden" }`, since nobody showed up to it |
| No mocks | Cannot rate yourself | Alice rates herself | 409 | Nothing is saved. Returns `{ msg: "rated_self", error: "You can't rate yourself" }` |
| No mocks | Can only rate players who showed up | Alice rates Ned, then Sam, then a user id nobody has | 404 | Nothing is saved. Returns `{ msg: "player_not_found" }` |
| No mocks | Only players who showed up can rate | Ned and Sam each rate Ben | 403 | Nothing is saved. Returns `{ msg: "forbidden" }` |
| No mocks | Cannot rate before the game is marked completed | Alice rates Ben in a game that started but isn't completed | 403 | Nothing is saved. Returns `{ msg: "forbidden" }` |
| No mocks | Can still rate just before a day has passed | Rated 23 hours 59 minutes after the game was completed | 201 | The rating is saved |
| No mocks | Cannot rate once a day has passed | Rated 24 hours 1 minute after the game was completed | 403 | Nothing is saved. Returns `{ msg: "rating_closed", error: "Rating for this game has closed" }` |
| No mocks | A player can only be rated once by the same player | Alice rates Ben 6 stars, then 1 star | 201, then 409 | Only the 6 star rating is saved. Returns `{ msg: "already_rated", error: "You already rated this player" }` the second time |
| No mocks | Two ratings sent at once save only one | Alice sends the same rating of Ben twice at the same time | 201 and 409 | One rating is saved |
| No mocks | Each player can rate each other player once | Ben already rated Alice. Alice rates Ben, and the organizer rates Ben | 201 | All three ratings are saved |
| No mocks | A player who left after the game was completed can still rate and be rated | Ben leaves, then Ben rates Alice and Alice rates Ben | 201 | Both ratings are saved |
| No mocks | The rater is always the signed in user | Alice sends a rating with `rater_id` set to the organizer | 201 | The rating is saved as Alice's |
| With mocks | Records a rating | `createRating` replaced | 201 | `createRating` gets the signed in user as the rater and the five categories |
| With mocks | Rejects missing and out of range categories | `skill` left out and `overall: 7`, `createRating` watched | 400 | The error names skill and overall. `createRating` is never called |
| With mocks | Each problem a rating can have | `createRating` returns `not_allowed`, `rating_closed`, `self`, `player_not_found` and `already_rated` | 403, 403, 409, 404, 409 | Returns `{ msg }` of `forbidden`, `rating_closed`, `rated_self`, `player_not_found` and `already_rated` |
| With mocks | Database fails | `createRating` throws | 500 | Returns `{ msg: "server_error", error: "Could not submit your rating, try again later" }` |

**GET /api/users/me/stats** (the user's own reputation score)

Each expected score follows the formula in Requirements and Design, section 4.1: (3 × 5 + Σ weight × game score) / (3 + Σ weight), rounded to two decimal places. The newest game has weight 1, and each older one 0.9 times the weight of the one after it.

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No score is read |
| No mocks | A new player is not rated yet | A user who never played | 200 | Returns `{ msg: "success", reputation_score: null }` |
| No mocks | A player nobody rated is not rated yet, even after playing | Alice played a game and rated Ben, but nobody rated her | 200 | `reputation_score` is null |
| No mocks | One game rated 6 by its only other player gives 5.25 | The organizer gave Alice 6 stars in her only game | 200 | `reputation_score` is 5.25: (15 + 6) / 4 |
| No mocks | Players who showed up but did not rate count as 5, no-shows do not count | The organizer gave Alice 6 stars. Ben and Cara didn't rate, and Ned didn't show up | 200 | `reputation_score` is 5.08: the game scores (6 + 5 + 5) / 3 |
| No mocks | A no-show counts as a game scoring 1 | Ned didn't show up to his only game | 200 | `reputation_score` is 4: (15 + 1) / 4 |
| No mocks | The game played most recently counts most | Two games: the one saved first was played later and got 6 stars, the other got 2 | 200 | `reputation_score` is 4.65, because games are ordered by when they were played. In the wrong order it would be 4.57 |
| With mocks | Returns the signed in user's score | `getReputationScore` replaced | 200 | Returns `{ msg: "success", reputation_score: 4.2 }`, looked up for the signed in user |
| With mocks | Needs a signed in user | No sign in, `getReputationScore` watched | 400 or above | No score is read |
| With mocks | Database fails | `getReputationScore` throws | 500 | Returns `{ msg: "server_error", error: "Could not load your rating, try again later" }` |

**Rating reminders** (checked by the server every minute, not called by the app)

| **Group** | **Test** | **Input** | **What should happen** |
| --------- | -------- | --------- | ---------------------- |
| No mocks | Players with someone left to rate are reminded once | A game completed 23.5 hours ago. Alice rated everyone, the organizer and Ben haven't, Ned didn't show up. The check runs twice | The organizer and Ben get one "Rating closes soon" reminder that opens the game. Alice and Ned get none. The game is marked reminded, so the second check sends nothing |
| No mocks | Games outside the last hour of rating are not reminded | A game completed 30 minutes ago, and one completed 24 hours 1 minute ago | Nobody is reminded, and neither game is marked reminded |
| No mocks | Nobody is reminded when everyone already rated | A game completed 23.5 hours ago where both players rated each other | The game is marked reminded, and nobody is notified |
| With mocks | Players with someone left to rate are reminded | `claimDueRatingReminders` returns two games, `getPlayersWithRatingsLeft` and `sendPushToUsers` replaced | Each game's players with someone left to rate get one reminder. Returns 2 |
| With mocks | Nothing is sent when no reminder is due | `claimDueRatingReminders` returns no games | Nobody is notified. Returns 0 |
| With mocks | The server checks every minute until it is stopped | Fake timers: two minutes pass, the reminders stop, two more minutes pass | 2 checks, none after stopping |
| With mocks | A failed check is logged and the next one still runs | Fake timers: `claimDueRatingReminders` throws once, then two minutes pass | The failure is logged as "Sending rating reminders failed:", and the next check still runs |

**Score rules** (unit tests of `reputationScore` in `backend/src/review/reputation.ts`, no database)

| **Test** | **Input** (games newest first) | **Expected output** |
| -------- | ------------------------------ | ------------------- |
| A player nobody rated and who never missed a game is not rated yet | No games, then two games nobody rated | `null` |
| One rated game moves the score from 5 only part of the way | One game rated 6, then one rated 1, by its only other player | 5.25, then 4 |
| Every rating is averaged across its five categories | One rating of 1, 2, 3, 4 and 5 stars | 4.5 |
| A game scores the average of its ratings, whatever order they were given in | Ratings of 1 and 6 stars, in both orders | 4.63 both times |
| A player who showed up but did not rate counts as giving 5 | One of three other players gave 6 stars | 5.08 |
| A game nobody rated scores 5, even if nobody else showed up | A game nobody rated, then a game rated 6 | 5.18 |
| Every game counts the same however many players rated it | A game rated 6 by eight players, then one rated 2 | The same as a game rated 6 by one player, then one rated 2 |
| A no-show counts as a game scoring 1 | One no-show | The same as one game rated 1 |
| A newer game counts more than an older one | Games rated 6 then 2, then 2 then 6 | 4.65, then 4.57 |
| Many games move the score toward what the player is rated | Ten games rated 6 | 5.68 |
| A game far from the rest counts a quarter as much once they showed up to four games | Games rated 1, 6, 6 and 6 | 5.25 |
| With fewer than four games no game is treated as unusual | Games rated 1, 6 and 6 | 4.6 |
| A game exactly 1.5 stars from the middle still counts in full, past it a quarter | A game scoring 4.5 (exactly 1.5 from 6), then 6, 6, 6. Then a game scoring 4.4 (1.6 from 6), then 6, 6, 6 | 5.3, then 5.4 |
| A no-show does not count toward the four games needed to find unusual games | Games rated 1, 6 and 6, then a no-show | 4.19 (as unusual it would be 4.61) |
| A no-show always counts in full, even far from the other games | A no-show, then four games rated 6 | 4.87 |
| The score stays between 1 and 6 | 50 no-shows, then 50 games rated 6 | At least 1, then at most 6 |

##### Notification component

The tests without mocks save notifications straight in the table with set times, so they can check the order. The routes that send notifications reply before the notification is saved, so the Game and Friend tests that check one wait for it to be saved (up to 3 seconds), and the ones that check none was saved wait a moment first.

**GET /api/notifications** (the user's notifications, for the Notifications screen)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No notifications are read |
| No mocks | User has no notifications | No notifications and no friend requests | 200 | Returns `{ msg: "success", notifications: [], unread_count: 0, friend_request_count: 0 }` |
| No mocks | Notifications are listed newest first | A read friend notification, an unread join request, and two unread invites made at the same time | 200 | Newest first, and notifications made at the same time newest id first. Each has `notification_id`, `type`, `title`, `body`, `game_id` (null for the friend notification), `created_at` and `is_read`. `unread_count` is 3 |
| No mocks | Only the user's own notifications are listed | Two notifications for another user and one for the user | 200 | Only the user's one is listed, and `unread_count` is 1 |
| No mocks | At most the newest 50 are listed, every unread one is counted | 55 unread notifications | 200 | 50 are listed, and `unread_count` is 55 |
| No mocks | Friend requests waiting for the user are counted | Two pending requests to the user, plus accepted and declined ones, one the user sent, and one between two other users | 200 | `friend_request_count` is 2, and friend requests aren't listed as notifications |
| No mocks | A deleted game's notifications are removed | A notification about a game and one that isn't, then the game is deleted | 200 | Only the one not about the game is listed |
| With mocks | Returns the notifications and both counts | `getNotifications`, `countUnreadNotifications` and `getIncomingFriendRequests` replaced | 200 | Each is read for the signed in user, and the notifications are sent with `notification_id`, `type`, `title`, `body`, `game_id`, `created_at` and `is_read` |
| With mocks | Database fails reading the notifications or the friend requests | `getNotifications`, then `getIncomingFriendRequests`, throws | 500 | Returns `{ msg: "server_error", error: "Could not load your notifications, try again later" }` |

**PATCH /api/notifications/read** (mark the user's notifications seen)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | The notification stays unread |
| No mocks | Marks every unread notification read | Two unread notifications and one read earlier | 200 | All three are read, the earlier one keeps when it was read, and `GET /api/notifications` then shows `unread_count` 0 |
| No mocks | Only the user's own notifications are marked read | An unread notification for the user and one for another user | 200 | Only the user's one is marked read |
| No mocks | Marking read twice changes nothing the second time | Mark read, then mark read again | 200 both times | The notification keeps when it was first read |
| No mocks | User has no notifications | No notifications | 200 | Returns `{ msg: "success" }` |
| With mocks | Marks the signed in user's notifications read | `markNotificationsRead` replaced | 200 | `markNotificationsRead` gets the signed in user. Returns `{ msg: "success" }` |
| With mocks | Database fails | `markNotificationsRead` throws | 500 | Returns `{ msg: "server_error", error: "Could not update your notifications, try again later" }` |

#### 2.1.2. Commit Hash Where Tests Run

_To be added at CP2._

#### 2.1.3. Explanation on How to Run the Tests

The tests without mocks need MySQL, which runs in Docker. The tests with mocks don't use the database. The tests can run on your own computer with npm, or inside the back-end Docker container.

**Setup (both ways)**

1. **Clone the repository:**
   ```
   git clone https://github.com/pawanpaddaeng/SportsNow_Project.git
   cd SportsNow_Project
   ```
2. **Add the environment files:** copy `.env.example` to `.env` in the root folder and fill it in, and add `backend/.env` with `JWT_SECRET`.
3. **Start Docker Desktop.** The first time MySQL starts, it creates the tables from `mysql/init/`.

**Running the tests on your computer with npm**

In our Docker setup MySQL is only reachable from inside Docker, so `docker-compose.test.yml` also opens it to your own computer, on `127.0.0.1:3307`. Before the tests run, `backend/tests/setupEnv.ts` reads the database login from the root `.env` and connects to that port.

1. **Install the back-end packages:**
   ```
   cd backend
   npm install
   cd ..
   ```
2. **Start the test database** from the root folder:
   ```
   docker compose -f docker-compose.yml -f docker-compose.test.yml up -d mysql-db
   ```
3. **Run all the tests** from the root folder. The script runs the tests with mocks, then without mocks, then both together. Each run saves a coverage report in `backend/coverage/mock`, `backend/coverage/no-mock` and `backend/coverage/interface`.
   - macOS / Linux: `./scripts/run-backend-interface-tests.sh`
   - Windows: `.\scripts\run-backend-interface-tests.ps1`

   Or run one set of tests from the `backend` folder:
   - Tests with mocks: `npx jest tests/mock`
   - Tests without mocks: `npx jest tests/no-mock`
   - One component: `npx jest tests/no-mock/game` or `npx jest tests/mock/friend`
   - The Review component's tests: `npx jest tests/mock/review tests/no-mock/review tests/unit/review`
   - One interface: `npx jest tests/no-mock/friend/searchUsers.test.ts`
4. **Stop the database** from the root folder when you're done:
   ```
   docker compose down
   ```

**Running the tests inside Docker**

The back-end container reaches MySQL directly, so it doesn't need `docker-compose.test.yml` or npm on your computer.

1. **Start MySQL and the back end** from the root folder. The container copies the code when it is built, so run this again after changing a test.
   ```
   docker compose up -d --build mysql-db backend
   ```
2. **Run the tests** from the root folder:
   - Tests with mocks: `docker compose exec backend npx jest tests/mock --coverageDirectory=coverage/mock`
   - Tests without mocks: `docker compose exec backend npx jest tests/no-mock --coverageDirectory=coverage/no-mock`
   - Both together: `docker compose exec backend npx jest tests/mock tests/no-mock --coverageDirectory=coverage/interface`
   - One component or interface: `docker compose exec backend npx jest tests/no-mock/game` or `docker compose exec backend npx jest tests/no-mock/friend/searchUsers.test.ts`

   The coverage reports are saved inside the container, in `/app/coverage/`.
3. **Stop the containers** when you're done:
   ```
   docker compose down
   ```

### 2.2. GitHub Actions Configuration Location

_To be added at CP2._

### 2.3. Jest Coverage Report Screenshots With Mocks

_To be added at CP2._

### 2.4. Jest Coverage Report Screenshots Without Mocks

_To be added at CP2._

---

## 3. Tests of Non-Functional Requirements

_To be added at CP2._

---

## 4. Front-end Test Specification

### 4.1. Location in Git of Front-end Test Suite:

`frontend/app/src/androidTest/java/com/example/cpen321application/e2e/`

| **Feature** | **Test file** |
| ----------- | ------------- |
| Friend System | [`FriendSystemTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/e2e/FriendSystemTest.kt) |
| Game Discovery | `DiscoverGameTest.kt` _(written at CP2)_ |

### 4.2. Tests

#### Running the tests on User A's device only

Some tests involve two users: User A is tested automatically on the emulator that runs the tests, and the manual tester acts as User B on another device. The tests run on every device connected to `adb`, so if User B uses an emulator on the same computer, the tests would also run as User B and fail. To prevent this, set `ANDROID_SERIAL` to User A's emulator, in the same terminal, before running the tests. An emulator's serial (such as `emulator-5556`) depends on which emulator was started first, so check it each time:

1. List the connected emulators and the name of each one. Use the serial whose name is User A's emulator (for example `Pixel_9`).

   PowerShell:
   ```powershell
   adb devices
   adb -s emulator-5554 emu avd name
   adb -s emulator-5556 emu avd name
   ```
   Bash:
   ```bash
   adb devices
   adb -s emulator-5554 emu avd name
   adb -s emulator-5556 emu avd name
   ```
2. Set it, using User A's serial:

   PowerShell:
   ```powershell
   $env:ANDROID_SERIAL = "emulator-5556"
   ```
   Bash:
   ```bash
   export ANDROID_SERIAL=emulator-5556
   ```
3. Run the tests from the same terminal. To use every connected device again, remove it with `Remove-Item Env:ANDROID_SERIAL` (PowerShell) or `unset ANDROID_SERIAL` (Bash).

If `adb` isn't found, it is in the Android SDK's `platform-tools` folder: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` on Windows, `~/Library/Android/sdk/platform-tools/adb` on macOS and `~/Android/Sdk/platform-tools/adb` on Linux.

If User B uses a real phone that isn't connected with USB debugging, `adb` can't see it and none of this is needed.

#### Doing User B's steps

When a test needs User B, it pauses until the manual tester has done the step on User B's device. A helper script shows each step in a second terminal. The helper only talks to User A's emulator, so User B can use any device: a real phone, or another emulator.

1. Open a second terminal in the repository folder. If you set `ANDROID_SERIAL` for the tests, set it in this terminal too. Then start the helper, before or after starting the tests:

   PowerShell:
   ```powershell
   .\scripts\run-e2e-user-b.ps1
   ```
   Bash:
   ```bash
   ./scripts/run-e2e-user-b.sh
   ```
2. Run the tests from the first terminal as usual.
3. Each time a test needs User B, the helper shows one line starting with `USER B:`, for example `USER B: Click the Friends tab, search for User A and click Add`. Do the step on User B's device, then press Enter in the helper's terminal. The test carries on and checks the result on User A's screen.
4. Stop the helper with Ctrl+C once the tests have finished.

A test fails if Enter isn't pressed within 10 minutes of a step being shown, or if User B's change doesn't show on User A's screen within 30 seconds of pressing Enter.

#### Use cases

The tests in `FriendSystemTest` run in a fixed order, by their names (`test1…`, `test2…`, `test3…`). The first two leave User A and User B with no friendship and no requests. `test3AcceptFriendRequestAndChat` makes them friends, so it runs last, and the tester removes the friendship after the tests until Remove Friend is built.

- **Use Case: Discover Game (Use Case 4)**

  - **Before the tests:**
    - **Signed in:** Each test starts signed in, on the Home screen.
    - **Test games:** Discover never shows your own games. So before the tests, `scripts/run-frontend-e2e-tests` runs a script that adds games to the database. The games belong to a test organizer, and are near a fixed test location. The script first removes the test games from earlier runs, so every run starts the same. It adds:
      - "E2E Basketball Near": basketball, 200 m away
      - "E2E Basketball Far": basketball, 1 km away
      - "E2E Soccer": soccer, 2 km away
      - no volleyball games
    - **Phone location:** The tests set the phone's location with a test location provider. They allow this with the command `appops set ... android:mock_location allow`.
    - **Location permission:** The tests give the app location permission before each test (`GrantPermissionRule`), so the permission pop-up doesn't appear.
    - **Location off (2a):** The 2a test turns the phone's location off with the command `cmd location set-location-enabled false`. It turns location back on at the end.

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Player selects the compass in the navigation bar. | Set the phone's location to the test location.<br>Click the "Discover" tab in the bottom bar.<br>Check that the text "Discover" is on the screen. |
    | 2. System gets the Player's location from the phone. | Check that "Showing games near" and "Your location" are on the screen.<br>Check that there is no location error message on the screen. |
    | 2a. System cannot get the Player's location (e.g. location is turned off or location access is denied). | Turn the phone's location off.<br>Click the "Discover" tab in the bottom bar. |
    | 2a1. System displays an error message and lets the Player try again or choose a place on the map. | Check that the text "Location is turned off. Turn it on in your phone's settings, then try again." is on the screen.<br>Check that the "Try again" button is on the screen.<br>Check that "Choose a location" and "Change" are on the screen.<br>Turn the phone's location back on.<br>Click the "Try again" button.<br>Check that the text "3 games nearby" is on the screen. |
    | 3. System displays the nearby games sorted by distance with their information, and a selector bar at the top with various sports. | Check that "3 games nearby" and "Sorted by distance" are on the screen.<br>Check that "1. E2E Basketball Near", "2. E2E Basketball Far" and "3. E2E Soccer" are on the screen, in that order.<br>Check that the first game shows "200 m".<br>Check that the "All", "Basketball", "Soccer" and "Volleyball" chips are on the screen, and "All" is selected. |
    | 3a. No games are found nearby. | Set the phone's location to a place more than 50 km from the test games.<br>Click the "Discover" tab in the bottom bar. |
    | 3a1. System displays a message saying no nearby games were found. | Check that the text "No games nearby right now. Check back later or organize your own game." is on the screen.<br>Check that there are no game cards on the screen. |
    | 4. Player selects a sport in the selector bar. | Click the "Basketball" chip. |
    | 5. System updates the list to show only the games of the selected sport. | Check that the text "2 basketball games nearby" is on the screen.<br>Check that "1. E2E Basketball Near" and "2. E2E Basketball Far" are on the screen.<br>Check that "E2E Soccer" is not on the screen. |
    | 5a. No nearby games are found for the selected sport. | Click the "Volleyball" chip. |
    | 5a1. System displays a message saying no games were found of the selected sport. | Check that the text "No volleyball games nearby. Try another sport." is on the screen.<br>Check that there are no game cards on the screen. |

  - **Test Logs:**
    ```
    To be added at CP2, once the test is written and run.
    ```

- **Use Case: Search Users (Use Case 4 of the Friend System, test `FriendSystemTest.test1SearchUsers`)**

  Search Users has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **User B (manual tester):** an account named "Bea Tester" exists, made with the app's sign up on another device that uses the same back end. No other user has this name. User A and User B are not friends and have no pending friend requests. User B does nothing during this test.
    - **One device:** User B uses another device, so the tests must only run on User A's emulator. See [Running the tests on User A's device only](#running-the-tests-on-user-as-device-only).

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. User selects "Friends" in the navigation bar. | Click the "Friends" tab in the bottom bar. |
    | 2. System displays the Friends screen with a search field and the user's friends. | Check that the text "Friends" is on the screen.<br>Check that a text field with the placeholder "Search by name" is on the screen.<br>Check that "Your Friends" is on the screen. |
    | 3. User types a name in the search field. | Input "Bea Tester" in the text field "Search by name". |
    | 4. System displays the matching users, best match first. | Wait until a card with the text "Bea Tester" is on the screen.<br>Check that the "Bea Tester" card is the first result. |
    | 3a. No user matches the name. | Clear the text field.<br>Input "Zqxv Wjkplm" in the text field "Search by name". |
    | 3a1. System displays a message saying nobody was found. | Wait until the text "No one found with that name." is on the screen.<br>Check that there are no person cards on the screen. |
    | 5. User clears the search. | Click the button "Clear search" (the × in the search field). |
    | 6. System shows the user's friends again. | Check that "Your Friends" is on the screen.<br>Check that the "Bea Tester" card is not on the screen. |

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Case: Send Friend Request (Use Case 5 of the Friend System, test `FriendSystemTest.test2SendFriendRequest`, which also covers Decline Friend Request)**

  Send Friend Request has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **User B (manual tester):** the "Bea Tester" account described for Search Users, signed in on another device that uses the same back end. User A and User B are not friends and have no pending friend requests. The test ends by declining the only request it creates, so it leaves the two users as it found them.
    - **One device:** see [Running the tests on User A's device only](#running-the-tests-on-user-as-device-only).
    - **User B's steps:** run the User B helper in a second terminal, see [Doing User B's steps](#doing-user-bs-steps). After each step is confirmed with Enter, the test searches again until User B's change shows on User A's screen.

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. User selects "Friends" in the navigation bar. | Click the "Friends" tab in the bottom bar. |
    | 2. User searches for the person by name. | Input "Bea Tester" in the text field "Search by name".<br>Wait until a card with the text "Bea Tester" is on the screen. |
    | 3. System shows the person with a button to add them. | Check that the "Bea Tester" card has a button labelled "Add". |
    | 4. User selects "Add". | Click the button labelled "Add" on the "Bea Tester" card. |
    | 5. System sends the request, shows it as sent, and notifies the other user. | Wait until the "Bea Tester" card shows "Request Sent".<br>Check that the "Bea Tester" card has no button labelled "Add".<br>Click the button "Clear search", then input "Bea Tester" in the text field "Search by name".<br>Check that the "Bea Tester" card still shows "Request Sent".<br>**(manual, User B)** Check that a "New friend request" notification arrived, saying User A sent a friend request.<br>**(manual, User B)** Open Notifications → Friend requests, check User A's request is listed, and click "Decline". Press Enter in the User B helper.<br>Search "Bea Tester" again until the card shows the "Add" button again. |
    | 3a. The other person already sent the user a request. | **(manual, User B)** Click the "Friends" tab, search for User A and click "Add". Press Enter in the User B helper.<br>Search "Bea Tester" again until the card shows "Sent you a request". |
    | 3a1. System shows that the person already sent a request instead of the "Add" button, and lets the user answer it. | Check that the "Bea Tester" card has no button labelled "Add".<br>Click "Sent you a request" on the "Bea Tester" card.<br>Check that the text "Friend Requests" is on the screen.<br>Wait until a request from "Bea Tester" is on the screen.<br>Click the button "Back". |

    The app never shows "Add" while a request is pending, so the server's "already requested" answer can't be reached from the screen. Step 5's second search, which still shows "Request Sent", covers that state.

    The same test then declines User B's request from 3a, which covers the Decline Friend Request use case below and leaves the two users with no requests for the next run.

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Case: Decline Friend Request (Use Case 7 of the Friend System, test `FriendSystemTest.test2SendFriendRequest`)**

  Decline Friend Request is tested at the end of the Send Friend Request test, with the request User B sends in its step 3a. It has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does. The only failure the app handles, a request that was already answered by the time it is declined, can't be caused from the screen, since the app has no way to withdraw a request.

  - **Before the tests:** as for Send Friend Request above. When these steps start, User B has a pending friend request to User A.

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. User opens their friend requests. | Click the "Notifications" tab in the bottom bar.<br>Click "Friend requests". |
    | 2. System lists the requests waiting for an answer, each with "Accept" and "Decline". | Check that the text "Friend Requests" is on the screen.<br>Wait until a request from "Bea Tester" is on the screen.<br>Check that the request has buttons labelled "Accept" and "Decline". |
    | 3. User selects "Decline". | Click the button labelled "Decline" on the request from "Bea Tester". |
    | 4. System removes the request, and the two users don't become friends. | Wait until the request from "Bea Tester" is no longer on the screen.<br>Click the "Friends" tab in the bottom bar, and click the button "Clear search" if a search is shown.<br>Check that "Your Friends" is on the screen, without a "Bea Tester" card.<br>Input "Bea Tester" in the text field "Search by name".<br>Check that the "Bea Tester" card has a button labelled "Add". |

  - **Test Logs:** the logs of `FriendSystemTest.test2SendFriendRequest` above.

- **Use Cases: Accept Friend Request (6), Send Friend Message (9, formal Use Case 1), Receive Friend Message (11) and View Friend Messages (10), test `FriendSystemTest.test3AcceptFriendRequestAndChat`**

  One test covers these four use cases in this order, since the two users can only chat once they are friends. Send Friend Message's scenario steps come from its formal specification (Requirements and Design section 3.5, Use Case 1). The other three have none, so their scenario steps describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **User B (manual tester):** the "Bea Tester" account described for Search Users, signed in on another device that uses the same back end. User A and User B are not friends and have no pending friend requests.
    - **One device:** see [Running the tests on User A's device only](#running-the-tests-on-user-as-device-only).
    - **User B's steps:** run the User B helper in a second terminal, see [Doing User B's steps](#doing-user-bs-steps). User B can send any message: the test only checks User A's messages by their text, and finds User B's by the sender name "Bea Tester" and where they are in the chat.
    - **User A's messages:** each run sends messages with the time in them (`E2E hello <time>`, `E2E offline <time>`). If the two users were friends before, the app reuses their old chat, so this keeps one run's messages apart from earlier ones.
    - **Notifications:** the test allows the app's notifications with `pm grant ... android.permission.POST_NOTIFICATIONS`, and uses UI Automator to clear old notifications and look for new ones in the notification shade. Push notifications must be set up for User A's emulator (the app's `google-services.json`).
    - **No connection (6a):** the 6a test turns the phone's Wi-Fi and mobile data off with UI Automator (`svc wifi disable`, `svc data disable`), and back on before trying again, waiting for the phone and the chat to reconnect. They are also turned back on after the test if it fails.
    - **After the test:** User A and User B are friends. Until Remove Friend is built, the tester removes the friendship so the next run starts again with no friendship.

  - **Expected Behaviors, Accept Friend Request (Use Case 6):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. The other person sends the user a friend request. | **(manual, User B)** Click the "Friends" tab, search for User A and click "Add". Press Enter in the User B helper. |
    | 2. User opens their friend requests. | Click the "Notifications" tab in the bottom bar.<br>Click "Friend requests". |
    | 3. System lists the requests waiting for an answer, each with "Accept" and "Decline". | Check that the text "Friend Requests" is on the screen.<br>Wait until a request from "Bea Tester" is on the screen.<br>Check that the request has a button labelled "Decline". |
    | 4. User selects "Accept". | Click the button labelled "Accept" on the request from "Bea Tester". |
    | 5. System removes the request, makes the two users friends, and notifies the other user. | Wait until the request from "Bea Tester" is no longer on the screen.<br>Click the "Friends" tab in the bottom bar, and click the button "Clear search" if a search is shown.<br>Wait until a "Bea Tester" card is on the screen.<br>**(manual, User B)** Check that a "Friend request accepted" notification arrived, and that User A is in User B's friends. Press Enter in the User B helper. |

  - **Expected Behaviors, Send Friend Message (Use Case 9, formal Use Case 1):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. User selects "Friends". | The Friends screen is open after accepting the request above. |
    | 2. System displays the list of the user's friends. | Check that "Your Friends" is on the screen.<br>Check that a "Bea Tester" card is on the screen. |
    | 3. User selects a friend. | Click the "Bea Tester" card. |
    | 4. System opens the chat with that friend. | Wait until the title "Bea Tester" is on the screen.<br>Check that a text field with the placeholder "Message" is on the screen.<br>Check that the button "Send message" is on the screen. |
    | 5a. User clicked "Send" without typing a message. | Check that the button "Send message" is disabled.<br>Input "   " (only spaces) in the text field "Message".<br>Check that the button "Send message" is still disabled. |
    | 5a1. System does not send anything and keeps the chat open. | Check that the title "Bea Tester" is still on the screen. |
    | 5. User types a message and clicks "Send". | Clear the text field "Message", then input "E2E hello &lt;time&gt;".<br>Check that the button "Send message" is enabled.<br>Click the button "Send message". |
    | 6. System sends the message to the friend | Check that the text field "Message" is empty again.<br>Wait until "E2E hello &lt;time&gt;" is on the screen and no message shows "Sending...".<br>**(manual, User B)** Open the chat with User A and check that User A's new message arrived. Press Enter in the User B helper. |
    | 6a. The message could not be sent (e.g. no internet connection). | Turn the phone's Wi-Fi and mobile data off.<br>Wait until "Connecting..." or "Couldn't reach the server, try again" is on the screen.<br>Input "E2E offline &lt;time&gt;" in the text field "Message" and click the button "Send message". |
    | 6a1. System displays a message saying the message could not be sent and lets the user try again. | Wait until the text "Not sent, check your connection. Tap to try again." is on the screen.<br>Turn Wi-Fi and mobile data back on, and wait for the phone to be online.<br>Wait until neither "Connecting..." nor "Couldn't reach the server, try again" is on the screen.<br>Click "Not sent, check your connection. Tap to try again.".<br>Wait until "E2E offline &lt;time&gt;" is on the screen and no message shows "Sending...".<br>Check that "Not sent, check your connection. Tap to try again." is no longer on the screen. |

  - **Expected Behaviors, Receive Friend Message (Use Case 11):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. The friend sends the user a message while the user has their chat open. | Keep the chat with "Bea Tester" open.<br>**(manual, User B)** In the chat with User A, send any message. Press Enter in the User B helper. |
    | 2. System shows the new message right away, from the friend. | Without leaving the chat, wait until a message showing the sender name "Bea Tester" is on the screen below "E2E offline &lt;time&gt;". |
    | 3. The friend sends a message while the user doesn't have the chat open. | Click the button "Back".<br>Open the notification shade and click "Clear all" (UI Automator), so an older notification can't count.<br>**(manual, User B)** Send User A any message. Press Enter in the User B helper. |
    | 4. System notifies the user with a push notification. | Open the notification shade (UI Automator).<br>Wait until a notification titled "Bea" is shown.<br>Close the notification shade. |

  - **Expected Behaviors, View Friend Messages (Use Case 10):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. User opens the chat with a friend. | Click the "Bea Tester" card on the Friends screen. |
    | 2. System shows the earlier messages, oldest at the top and newest at the bottom, each with who sent it. | Wait until "E2E offline &lt;time&gt;" is on the screen.<br>Check that "E2E hello &lt;time&gt;" is above "E2E offline &lt;time&gt;".<br>Wait until at least two messages showing the sender name "Bea Tester" are on the screen below "E2E offline &lt;time&gt;". |

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```
