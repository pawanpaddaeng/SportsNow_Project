# Testing

## 1. Change History

| **Change Date** | **Modified Sections** | **Rationale** |
| --------------- | --------------------- | ------------- |
| Oct 20, 2026 | 2.1, 4 | First test plan for CP1. Section 2.1 plans the back-end tests for the Game component. Section 4 plans the end-to-end test for Use Case 2, Discover Game. We will write and run the tests, and add the results, coverage, GitHub Actions file and logs, at CP2. |
| Nov 17, 2026 | 2, 2.1 | The back-end tests for the Game and Friend components are written. Each interface now has one test file without mocks and one with mocks, so section 2.1.1 gives those files and the line where each `describe` group starts. The test tables now match the tests that were written, and the Friend component's tests are added. Section 2.1.3 explains how to start the test database and run the tests. |
| Nov 17, 2026 | 2, 2.1 | The back-end tests for the Review component are written: its three interfaces, the rating reminder the server checks for every minute, and the score rules. The existing mocked rating tests are split into one file per interface, and each interface gets a test file without mocks. New edge cases cover the 1 to 6 star limits, values that aren't whole numbers, the edges of the 1-day rating window, games that aren't completed, players who didn't show up or left, rating twice or at the same time, the order games count in, and a game exactly 1.5 stars from a player's middle score. The Reputation score case of the join request list now gives the exact score its test checks, 3.86. |
| Nov 18, 2026 | 2, 2.1 | The back-end tests for the User component are written: sign up, login, registering a device for notifications, and reading, editing and deleting the user's own profile each get a test file without mocks and one with mocks. The public profile, which only had tests with mocks, gets a test file without mocks, and its tests with mocks are now listed here. A real sign up or login needs an ID token that Google signed, so the tests without mocks stop at Google's check, and the tests with mocks replace that check to test a successful one. |
| Nov 18, 2026 | 2, 2.1 | The back-end tests for the Notification component are written (issue #83): its two interfaces, `GET /api/notifications` and `PATCH /api/notifications/read`, and the game start reminder the server checks for every minute, with unit tests for how the reminder says how soon the game starts. The join request, invite and friend request tests now also check the notification each one saves, and the decline friend request tests check that none is. Added an unmocked test for inviting friends successfully. Tests with mocks now replace saving notifications before every test, so they never reach the database. The start reminder tests found a deadlock with other transactions on `games`, fixed by skipping locked games (`FOR UPDATE SKIP LOCKED`). The describe group locations of the changed Game and Friend tests are updated. |
| Nov 18, 2026 | 4.1, 4.2 | Added the front-end tests for the Friend System (`FriendSystemTest.kt`): Search Users, Send and Decline Friend Request, Accept Friend Request, and Send (formal Use Case 1), Receive and View Friend Messages. Section 4.2 explains how the manual tester acts as User B with `scripts/run-e2e-user-b`, and how to run the tests on User A's emulator only. Logs will be added once the tests pass. |
| Nov 18, 2026 | 2.2 | Added the GitHub Actions workflow that runs the back-end interface tests on pushes to `main`, on pull requests, and by hand. |
| Nov 18, 2026 | 2, 2.1 | The back-end tests for the Message component are written (issue #63): `GET /api/chats/{chatId}/messages` and the WebSocket `send_message` request, each with a test file without mocks and one with mocks, plus unit tests for how a `send_message` request is checked. New edge cases cover paging with `before`, chats of games that ended, players removed and friendships ended while a chat is open, a chat deleted while open, sending to a different chat than the connection is for, the 1000 character limit, the friend message notification and its 100 character preview, database and notification failures, and dropping connections that stop answering pings. Section 2.1.3 adds the command to run the Message tests. |
| Nov 18, 2026 | 3 | The tests of the non-functional requirements are written, a back-end test and a front-end test for each. Performance: a Jest test that times Discover with 1,000 nearby games, and an Android instrumented test that times Discover on the phone, from selecting the compass to the list being shown. Chat Responsiveness: a Jest test that times 20 messages between two members over the chat WebSocket, and an Android instrumented test that times 20 messages on the phone, from tapping Send to the server's copy being shown. Section 3 gives where they are, what they check and how to run them. |
| Nov 18, 2026 | 3 | The Performance requirement is replaced by Security (see Requirements and Design 3.7), so its tests are replaced. Four Jest tests check that every route is in the list the tests check, that every protected route turns down 6 kinds of bad token without changing anything, that a signed in user can't read the chat of, or change, a game they aren't in, and that the chat WebSocket turns down bad tokens and users who aren't in the chat. An Android instrumented test checks that the app goes back to sign in and clears its token when the server turns the token down. The Chat Responsiveness back-end test moved to port 18104, because 18103 is also used by a chat test with mocks that runs at the same time. |
| Nov 18, 2026 | 4.1, 4.2 | Added the front-end tests for Game Discovery and game chat (issue #80): Discover Game (formal Use Case 4) with its failures 2a, 3a and 5a, Join Game (formal Use Case 5) with 3a, 4a, 4b and 4c, View Upcoming Games, Leave Game, and Send, View and Receive Message in a game chat, where User B sends the live message. The planned Discover Game test from CP1 is replaced by the one that was written. Section 4.2 explains the test games that `scripts/run-frontend-e2e-tests` now saves before the tests with `backend/tests/e2e/seedGameDiscovery.ts`, and how the tests set the phone's location and act as the games' organizer. Logs will be added once the tests pass. |
| Nov 18, 2026 | 2.1, 4.2 | Added the back-end tests for `DELETE /api/friends/{userId}` (issue #75), a test file without mocks and one with mocks. They cover signing in, bad ids, removing yourself, removing someone who isn't a friend, a successful removal deleting the chat and its messages, removing twice, a removed friend who has the chat open being disconnected, and becoming friends again with a new, empty chat. In section 4.2, the Friend System tests no longer need the tester to remove the friendship by hand: they use Remove friend in the app, and a new chat starts empty, so old messages can't mix with a new run's. |
| Nov 18, 2026 | 4.1, 4.2 | Added the front-end tests for Game Planning: Create Game (formal Use Case 3) with its failures 6a, 7a, 7b and 8a in `CreateGameTest.kt`, and Edit Game, Cancel Game, Invite Friends, Accept Join Request, Reject Join Request and Remove Player in `GamePlanningTest.kt`. 6a and 7a follow the updated Use Case 3 in Requirements and Design. 4a and 8b can't be tested end to end, and section 4.2 says why. `backend/tests/e2e/seedGameDiscovery.ts` now also saves User A's games, friends, join requests and player for these tests. The E2E helper files, such as `ComposeWaits.kt` and `ToastWaits.kt`, are in `androidTest/.../testutil/`, so `scripts/run-frontend-e2e-tests` doesn't run them as test classes. Logs will be added once the tests pass. |

---

## 2. Back-end Test Specification: APIs

We test every interface of the **User**, **Game**, **Friend** and **Review** components that is built (see Requirements and Design, section 4.1). The Review component also has a timed part the app never calls, the rating reminder the server checks for every minute, which is tested the same way. The test folders are named after the back-end components in `backend/src`, so the User tests are in `user`, the Game tests in `game`, the Friend tests in `friend` and the Review tests in `review`.

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
- The User tests can't sign up or log in through Google, since that needs an ID token Google signed for this app. So their sign up and login tests stop at Google's check, which turns down a made-up token. The delete account test watches `sendPushToUsers` without replacing it, like the Review tests.
- The Review tests mark a game completed with the same `completeGame` the organizer's request uses, then move the time it was completed back to test the 1-day rating window. To see who is sent a rating reminder, they watch `sendPushToUsers` with `jest.spyOn` without replacing it. The test users have no devices, so no notification leaves the server. Other test files' games can be due for a reminder at the same time, so these tests only check their own games.
- The tests of routes that send a notification check the row saved in `notifications`. The route replies before saving it, so the test waits for it.

**Tests with mocks** don't use the database. They replace the database settings with `backend/tests/mock/mockEnv.ts`, and sign in by mocking the user lookup. They use `jest.spyOn` to:
- replace Google's check of the ID token, so a sign up or login can succeed (`backend/tests/mock/user/googleHelpers.ts`)
- make database functions throw an error, to check that the server answers `500 server_error`
- replace the push notification functions (`sendPushToUser` and `sendPushToUsers`), to check who gets a notification and what type it is
- replace `saveNotifications`, to check which notifications are saved for the Notifications screen. It is replaced before every test with mocks (in `cleanUpAfterTests`), so no test with mocks tries to reach the database
- make a push notification fail, to check the server still answers and logs the failure
- replace the database transaction with a fake one, to test a request that is deleted while the server waits for it
- The rating reminder's tests with mocks also use Jest's fake timers, so the server's check every minute can run without waiting.

### 2.1. Locations of Back-end Tests and Instructions to Run Them

#### 2.1.1. Tests

**User component**

| **Interface** | **Describe Group Location, No Mocks** | **Describe Group Location, With Mocks** | **Mocked Components** |
| ------------- | ------------------------------------- | --------------------------------------- | --------------------- |
| **POST /api/auth/signup** | [`backend/tests/no-mock/user/signup.test.ts#L16`](../backend/tests/no-mock/user/signup.test.ts#L16) | [`backend/tests/mock/user/signup.test.ts#L21`](../backend/tests/mock/user/signup.test.ts#L21) | Google Authentication, Users DB |
| **POST /api/auth/login** | [`backend/tests/no-mock/user/login.test.ts#L9`](../backend/tests/no-mock/user/login.test.ts#L9) | [`backend/tests/mock/user/login.test.ts#L12`](../backend/tests/mock/user/login.test.ts#L12) | Google Authentication, Users DB |
| **POST /api/register_notif** | [`backend/tests/no-mock/user/registerNotif.test.ts#L19`](../backend/tests/no-mock/user/registerNotif.test.ts#L19) | [`backend/tests/mock/user/registerNotif.test.ts#L10`](../backend/tests/mock/user/registerNotif.test.ts#L10) | Device Tokens DB |
| **GET /api/users/me** | [`backend/tests/no-mock/user/getMyProfile.test.ts#L13`](../backend/tests/no-mock/user/getMyProfile.test.ts#L13) | [`backend/tests/mock/user/getMyProfile.test.ts#L10`](../backend/tests/mock/user/getMyProfile.test.ts#L10) | Users DB |
| **PUT /api/users/me** | [`backend/tests/no-mock/user/updateMyProfile.test.ts#L23`](../backend/tests/no-mock/user/updateMyProfile.test.ts#L23) | [`backend/tests/mock/user/updateMyProfile.test.ts#L12`](../backend/tests/mock/user/updateMyProfile.test.ts#L12) | Users DB |
| **DELETE /api/users/me** | [`backend/tests/no-mock/user/deleteAccount.test.ts#L38`](../backend/tests/no-mock/user/deleteAccount.test.ts#L38) | [`backend/tests/mock/user/deleteAccount.test.ts#L45`](../backend/tests/mock/user/deleteAccount.test.ts#L45) | Users DB, Games DB, Firebase push |
| **GET /api/users/{userId}** | [`backend/tests/no-mock/user/publicProfile.test.ts#L18`](../backend/tests/no-mock/user/publicProfile.test.ts#L18) | [`backend/tests/mock/user/publicProfile.test.ts#L23`](../backend/tests/mock/user/publicProfile.test.ts#L23) | Users DB, Friends DB, Reputation DB |

The unmocked User tests share their setup helpers in [`backend/tests/no-mock/user/userHelpers.ts`](../backend/tests/no-mock/user/userHelpers.ts), and check a call needs a signed in user with `expectSignInRequired` in [`backend/tests/no-mock/helpers.ts`](../backend/tests/no-mock/helpers.ts). The mocked sign up and login tests replace Google's check with [`backend/tests/mock/user/googleHelpers.ts`](../backend/tests/mock/user/googleHelpers.ts).

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
| **DELETE /api/friends/{userId}** | [`backend/tests/no-mock/friend/removeFriend.test.ts#L42`](../backend/tests/no-mock/friend/removeFriend.test.ts#L42) | [`backend/tests/mock/friend/removeFriend.test.ts#L11`](../backend/tests/mock/friend/removeFriend.test.ts#L11) | Friends DB, MySQL transaction |

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

**Message component** (game and friend chats)

| **Interface** | **Describe Group Location, No Mocks** | **Describe Group Location, With Mocks** | **Mocked Components** |
| ------------- | ------------------------------------- | --------------------------------------- | --------------------- |
| **GET /api/chats/{chatId}/messages** | [`backend/tests/no-mock/chat/getChatMessages.test.ts#L41`](../backend/tests/no-mock/chat/getChatMessages.test.ts#L41) | [`backend/tests/mock/chat/getChatMessages.test.ts#L13`](../backend/tests/mock/chat/getChatMessages.test.ts#L13) | Chats DB |
| **WebSocket `send_message`** (`wss://DOMAIN/ws?chat_id=`) | [`backend/tests/no-mock/chat/sendMessage.test.ts#L55`](../backend/tests/no-mock/chat/sendMessage.test.ts#L55) | [`backend/tests/mock/chat/sendMessage.test.ts#L73`](../backend/tests/mock/chat/sendMessage.test.ts#L73) | Chats DB, Users DB, Firebase push, Logger, timers |

The unmocked Message tests share their setup helpers in [`backend/tests/no-mock/chat/chatHelpers.ts`](../backend/tests/no-mock/chat/chatHelpers.ts). Both WebSocket test files connect with the test client in [`backend/tests/chatSocketClient.ts`](../backend/tests/chatSocketClient.ts). How a `send_message` request is checked has no database code, so it also has unit tests, in [`backend/tests/unit/chat/sendMessage.test.ts#L9`](../backend/tests/unit/chat/sendMessage.test.ts#L9).

#### Test cases

**Signing in**

Every route checks that the user is signed in first, in the same middleware. The User, Friend and Review components' groups without mocks test it for each of their interfaces that needs a signed in user:

| **Test** | **Input** | **Expected status** | **What should happen** |
| -------- | --------- | ------------------- | ---------------------- |
| Rejects a call that is not signed in | No `Authorization` header, then `Authorization: Bearer not-a-real-token` | 400, then 401 | Nothing changes. Returns `{ msg: "invalid_request" }`, then `{ msg: "invalid_token", error: "Invalid jwt_token" }` |

##### User component

The tests without mocks use real users the test adds, named Alice, Ben and Olivia, all born in 1995 with the last name Tester.

**POST /api/auth/signup** (create an account)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a sign up with missing fields | No body, then `id_token` and `email` with no `f_name`, `l_name` or `birthYear` | 400 | Google is never asked and no user is saved. Returns `{ msg: "invalid_request" }` with `error` "id_token and email are required", then "First Name, Last Name and Birth Year fields are compulsory" |
| No mocks | Rejects an ID token Google did not sign | Every field filled in, with a made-up `id_token` | 401 | No user is saved. Returns `{ msg: "invalid_token", error: "Invalid Google ID token" }` |
| With mocks | Creates the account and signs the user in | Every field filled in. Google's check is replaced to accept the token, no user has the Google account, and `createUser` saves user 7 | 201 | `createUser` gets the Google account's id and email and the profile. Returns `{ msg: "signedIn", jwt_token }`, and the JWT signs in user 7 |
| With mocks | Google account already has a user | `userExists` says yes, `createUser` watched | 409 | No second user is saved. Returns `{ msg: "already_exists" }` |
| With mocks | Email does not match the Google account | An `email` that isn't the Google account's | 400 | No user is saved. Returns `{ msg: "invalid_request", error: "email does not match the Google account" }` |
| With mocks | Database fails while saving the user | `createUser` throws | 500 | Returns `{ msg: "server_error", error: "Could not create account, try again later" }` |

**POST /api/auth/login** (sign in)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a login with no ID token | No body, then an empty `id_token` | 400 | Google is never asked. Returns `{ msg: "invalid_request", error: "id_token is required" }` |
| No mocks | Rejects an ID token Google did not sign | A made-up `id_token` | 401 | No JWT is made. Returns `{ msg: "invalid_token", error: "Invalid Google ID token" }` |
| With mocks | Signs in a user who has an account | Google's check is replaced to accept the token, `findUserByGoogleId` finds player 2 | 200 | The user is looked up by the Google account that signed the token. Returns `{ msg: "signedIn", jwt_token }`, and the JWT signs in player 2 |
| With mocks | Google account has no user yet | `findUserByGoogleId` finds nobody | 404 | No JWT is made, so the app sends them to sign up. Returns `{ msg: "no_user" }` |
| With mocks | Database fails while looking up the user | `findUserByGoogleId` throws | 500 | Returns `{ msg: "server_error", error: "Could not sign in, try again later" }` |

**POST /api/register_notif** (save the device's token for push notifications)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No device token is saved |
| No mocks | Saves the device token for the signed in user | Alice sends her device's `fcm_token` | 200 | The token is saved for Alice. Returns `{ msg: "success" }` |
| No mocks | Rejects a missing device token | No `fcm_token`, then one of only spaces | 400 | No token is saved. Returns `{ msg: "invalid_request", error: "fcm_token is required (max 512 characters)" }` |
| With mocks | Saves the trimmed token for the signed in user | `fcm_token: " device-token "`, `saveDeviceToken` replaced | 200 | `saveDeviceToken` gets "device-token" and the signed in user's id. Returns `{ msg: "success" }` |
| With mocks | Database fails while saving the token | `saveDeviceToken` throws | 500 | Returns `{ msg: "server_error", error: "Could not register notifications, try again later" }` |

**GET /api/users/me** (the user's own profile)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No profile is read |
| No mocks | Returns the signed in user's profile | Alice asks for her profile | 200 | Returns `{ msg: "success", user }` with her `user_id`, `email`, `f_name`, `l_name`, `birthYear` and `created_at` |
| With mocks | Returns the signed in user's profile | The sign in check's `findUserById` returns player 2 | 200 | Returns player 2's details. Nothing else is read |
| With mocks | Database fails while checking the sign in | `findUserById` throws | 500 | No profile is sent. Returns `{ msg: "server_error", error: "Could not check sign in, try again later" }` |

**PUT /api/users/me** (edit the user's own profile)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | The profile doesn't change |
| No mocks | Updates the signed in user's profile | Alice sends `{ f_name: "Alicia", l_name: "Newname", birthYear: 1990 }` | 200 | The new values are saved. Returns `{ msg: "success", user }` with them |
| No mocks | Rejects a profile with a missing field | `f_name` and `birthYear`, no `l_name` | 400 | The profile doesn't change. Returns `{ msg: "invalid_request", error: "First Name, Last Name and Birth Year fields are compulsory" }` |
| With mocks | Saves the new profile for the signed in user | A valid profile, `updateUserProfile` replaced | 200 | `updateUserProfile` gets the signed in user's id and the new values. Returns the user with the new values |
| With mocks | Invalid profile never reaches the database | No `l_name`, `updateUserProfile` watched | 400 | Returns `{ msg: "invalid_request" }`. `updateUserProfile` is never called |
| With mocks | Database fails while saving the profile | `updateUserProfile` throws | 500 | Returns `{ msg: "server_error", error: "Could not update profile, try again later" }` |

**DELETE /api/users/me** (delete the user's account)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No account is deleted |
| No mocks | Deletes the account and the games the user organizes | Olivia organizes an upcoming game Ben plays in, and plays in Ben's upcoming game. She deletes her account, then uses her old token again | 200, then 401 | Olivia's account and her game are deleted. She is no longer in Ben's game, which stays, and Ben's account stays. Ben is told her game was cancelled (`game_cancelled`) and that she left his (`player_left`). Returns `{ msg: "success" }`, then `{ msg: "no_user" }` for her old token |
| With mocks | Players of the user's games are told | `getActiveGamesOfUser` returns the organizer's game and a game of user 2 they joined, `getUsersToNotify` returns users 2 and 3, `deleteUser` and `sendPushToUsers` replaced | 200 | `deleteUser` gets the organizer's id. Users 2 and 3 get `game_cancelled` ("... was cancelled, the organizer deleted their account"), and user 2 gets `player_left` ("Olivia left ...") |
| With mocks | Database fails while deleting the account | `deleteUser` throws, `sendPushToUsers` watched | 500 | Nobody is notified. Returns `{ msg: "server_error", error: "Could not delete account, try again later" }` |

**GET /api/users/{userId}** (another player's public profile)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No profile is read |
| No mocks | Returns only the public details of another player | Alice opens Ben's profile. Ben never played and isn't her friend | 200 | Returns his name, `age_range`, `games_played: 0`, `reputation_score: null`, `badges: []` and `friendship_status: "none"`. His email and birth year are never sent |
| No mocks | Shows a friend as a friend | Alice and Ben are friends, Alice opens Ben's profile | 200 | `friendship_status` is "friends" |
| No mocks | Own profile is marked self | Alice opens her own profile | 200 | `friendship_status` is "self" |
| No mocks | Player does not exist | A user id nobody has | 404 | Returns `{ msg: "user_not_found", error: "This player does not exist" }` |
| No mocks | Rejects a bad user id | `/api/users/abc` | 400 | Returns `{ msg: "invalid_request", error: "userId must be a positive whole number" }` |
| With mocks | Returns only the public details of another player | The organizer opens player 2's profile. `findUserById`, `getReputation` (4 games, score 4.2) and `getFriendshipStatus` ("request_sent") replaced | 200 | Returns exactly the public fields. The email and birth year are never sent |
| With mocks | Own profile is marked self | The organizer opens their own profile, `getFriendshipStatus` watched | 200 | `friendship_status` is "self", and no friendship is looked up |
| With mocks | Unknown player | A user id nobody has, `getReputation` watched | 404 | Returns `{ msg: "user_not_found" }`. No reputation is read |
| With mocks | Invalid id is rejected | Ids `0`, `abc` and `1.5` | 400 | Returns `{ msg: "invalid_request" }`. Nothing is read |
| With mocks | Database error | `getReputation` throws | 500 | No profile is sent. Returns `{ msg: "server_error" }` |
| With mocks | Age range shown instead of the birth year | Birth years 2007, 2002, 2001, 1992, 1962 and 1961, in 2026 | n/a | "19–24", "19–24", "25–34", "25–34", "55–64" and "65+" |

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

**DELETE /api/friends/{userId}** (remove a friend)

Every test starts with two friends who share a chat holding 3 messages.

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | They stay friends and keep their chat and its messages |
| No mocks | Bad userId | A `userId` of "abc", 0, -3 or 1.5 | 400 | They stay friends. Returns `{ msg: "invalid_request" }` |
| No mocks | Cannot remove yourself | The user's own `userId` | 409 | Nothing changes. Returns `{ msg: "own_friend" }` |
| No mocks | Not friends with this user | A user who isn't a friend, and a `userId` no user has | 404 | Nothing changes, the two friends keep their chat. Returns `{ msg: "not_friends" }` |
| No mocks | Removes the friend and deletes the chat | The friend's `userId` | 200 | The friendship is removed both ways, the chat and its 3 messages are deleted, neither sees the other in `GET /api/friends`, and both get `404 chat_not_found` loading the old chat |
| No mocks | Removing twice | The same friend removed again | 200, then 404 | The second call returns `{ msg: "not_friends" }` |
| No mocks | Removed friend with the chat open is disconnected | The friend has the chat open over the WebSocket, is removed, then sends a message | 200 | Nothing is saved. The friend gets `{ type: "error", msg: "chat_not_found" }` and the connection is closed with 4403 |
| No mocks | Friends again get a new, empty chat | After the removal, the removed friend sends a new request and it's accepted | 200, 201, 200 | They're friends again with a new chat whose `chat_id` is different, listed in `GET /api/friends`, with no messages |
| With mocks | Database fails | `removeFriend` throws | 500 | Returns `{ msg: "server_error", error: "Could not remove this friend, try again later" }` |
| With mocks | Database fails while removing | A fake transaction: both users are locked, then deleting the friendship fails | 500 | The chat is never deleted. Returns `{ msg: "server_error" }` |


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

##### Message component

The tests without mocks save messages straight in the `chat_messages` table, and take players out of games and end friendships straight in the database, to test what happens to a chat that is already open. The WebSocket tests start the chat server on a port of their own, since test files run at the same time, and connect to it with a real `ws` client, as the app does. Sending a message replies over the WebSocket, not with an HTTP status, so those tables give the reply instead.

**GET /api/chats/{chatId}/messages** (a page of a chat's earlier messages)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Rejects a call that is not signed in | See "Signing in" above | 400, then 401 | No messages are read |
| No mocks | New game chat has no messages | A player opens the chat of a new game | 200 | Returns `{ msg: "success", messages: [], is_closed: false }` |
| No mocks | Player reads the game chat, newest first | The organizer sends "Hi all", then the player sends "See you there" | 200 | Both are listed, newest first. Each has `message_id`, `chat_id`, `sender` (`user_id`, `first_name`, `last_name`), `text` and `created_at` (UTC, no milliseconds). `is_closed` is false |
| No mocks | Older messages load a page at a time with before | 55 messages. The newest page, then `?before=` the oldest message on it, then `?before=` the first message | 200 each time | The newest 50 (55 to 6), then the 5 older ones (5 to 1), then `[]`. No message is skipped or repeated |
| No mocks | Both friends read their friend chat | Two friends, one message from each | 200 for both | Both see the same two messages, newest first. `is_closed` is false, a friend chat never closes |
| No mocks | Chat of a completed or cancelled game is closed but readable | A completed game and a cancelled game, each with one message | 200 | The message is listed, and `is_closed` is true |
| No mocks | Rejects a chatId that is not a valid id | `chatId` "abc", "0", "-1", "1.5" and "99999999999" | 400 | Returns `{ msg: "invalid_request", error: "chatId must be a positive whole number" }` |
| No mocks | Rejects a before that is not a valid id | `?before=` "abc", "0", "-5" and empty | 400 | Returns `{ msg: "invalid_request", error: "before must be a positive whole number" }` |
| No mocks | Chat does not exist | A `chatId` no chat has | 404 | Returns `{ msg: "chat_not_found", error: "This chat does not exist" }` |
| No mocks | User who is not a player cannot open the game chat | A user who never played the game | 403 | The messages aren't shown. Returns `{ msg: "forbidden", error: "You can't open this chat" }` |
| No mocks | Player who left the game cannot open its chat any more | The player is taken out of the game | 403 | Returns `{ msg: "forbidden", error: "You can't open this chat" }` |
| No mocks | Stranger or former friend cannot open a friend chat | A user who isn't one of the two friends, then one of the friends after they stop being friends | 403 both times | Returns `{ msg: "forbidden", error: "You can't open this chat" }` |
| With mocks | Database fails checking access to the chat | `getChatAccess` throws | 500 | The messages are never read. Returns `{ msg: "server_error", error: "Could not load messages, try again later" }` |
| With mocks | Database fails reading the messages | `getChatAccess` lets the user in, `getChatMessages` throws. `?before=120` | 500 | `getChatMessages` was asked for the page before message 120. Returns `{ msg: "server_error", error: "Could not load messages, try again later" }` |

**WebSocket `send_message`** (send a message to the chat that is open)

| **Group** | **Test** | **Input** | **Expected reply** | **What should happen** |
| --------- | -------- | --------- | ------------------ | ---------------------- |
| No mocks | Rejects connections that may not open the chat | Connections with no `chat_id`, `chat_id` "abc", no token, a token that isn't real, a `chat_id` no chat has, and a user who isn't a player | HTTP 400, 400, 400, 401, 404, then 403 | The connection never opens |
| No mocks | Plain HTTP request is answered 426 | A plain HTTP request, not a WebSocket | HTTP 426 | The chat server only accepts WebSocket connections |
| No mocks | Message is saved and sent to everyone in the chat | The organizer and the player are connected, and the player also has a second game's chat open. The player sends `{ chat_id, text: "On my way", client_id: "c-1" }` | `{ type: "new_message", message, client_id: "c-1" }` to both | The message is saved. `message` has the same fields as in `GET /api/chats/{chatId}/messages`. The other chat's connection gets nothing |
| No mocks | Outer spaces are trimmed and a missing client_id comes back null | `text: "   Running late   "`, no `client_id` | `new_message` with `client_id: null` | "Running late" is saved and sent |
| No mocks | Text of 1000 characters is sent, 1001 is rejected | 1000 characters with spaces around them, then 1001 characters | `new_message`, then `{ type: "error", msg: "invalid_request", error: "text must be at most 1000 characters", client_id: null }` | Only the first is saved. The connection stays open |
| No mocks | Bad requests are answered invalid_request | A request that isn't JSON, one with `type: "typing"`, and one with text "   " | `{ type: "error", msg: "invalid_request", error, client_id: null }` for each | The error is "Request must be a JSON object", "type must be send_message", then "text must not be empty". Nothing is saved, the connection stays open |
| No mocks | Sending to a different chat than the connection is for | The connection is for the game chat, the player sends to another game's chat they are also in | `{ type: "error", msg: "forbidden", error: "This connection is for a different chat", client_id: "c-2" }` | Nothing is saved in either chat. The connection stays open |
| No mocks | Chat of a game that ended cannot be written to | A completed game's chat is opened, and another game is cancelled while its chat is open. A message is sent on each | `{ type: "error", msg: "chat_closed", error: "This chat is closed because the game ended", client_id }` for each | A closed chat can still be opened, but nothing is saved |
| No mocks | Player removed while connected cannot send | The player connects, is taken out of the game, then sends | `{ type: "error", msg: "forbidden", error: "You can't use this chat", client_id: "c-3" }` | Nothing is saved. The server closes the connection with code 4403, "Not a member of this chat" |
| No mocks | Removed player is disconnected instead of getting new messages | Both are connected, the player is taken out of the game, then the organizer sends | `new_message` to the organizer only | The removed player's connection is closed with code 4403 and gets no message |
| No mocks | Chat deleted while connected | Two friends' chat is deleted while one has it open (as when the other deletes their account), then they send | `{ type: "error", msg: "chat_not_found", error: "This chat does not exist", client_id: "c-4" }` | The connection is closed with code 4403, "This chat does not exist" |
| No mocks | Friend is notified of a message unless they have the chat open | In a friend chat the player sends "Lunch?" while the friend doesn't have it open, then again once they do. `sendPushToUsers` is watched without replacing it, the test users have no devices | `new_message` | The first time the friend is sent a push: title "Pat", body "Lunch?", data `{ type: "friend_message", chatId, friendName: "Pat Tester" }`, tag `friend-<sender id>`. The second time nobody is left to notify |
| No mocks | Game chat messages send no notification | The player sends in the game chat while the organizer isn't connected | `new_message` | `sendPushToUsers` is never called |
| With mocks | Database fails while checking a new connection | `getChatAccess` throws | HTTP 500 | The connection never opens. The failure is logged as "Opening a chat connection failed:" |
| With mocks | Database fails saving the message | `createChatMessage` throws | `{ type: "error", msg: "server_error", error: "Could not send the message", client_id: "c-1" }` | The failure is logged as "Sending to chat 7 failed:". The connection stays open |
| With mocks | Database fails reading who is in the chat after saving | Both users are connected, `createChatMessage` saves the message, `getChatMemberIds` throws | `new_message` to the sender only | The message is already saved, so the sender is told it's sent. The other user gets it when their chat next loads. The failure is logged |
| With mocks | Friend notification shows the first 100 characters | A friend chat. `sendPushToUsers` replaced. The sender sends 100 "a"s, then 101 emoji | `new_message` both times | The first push body is the 100 "a"s with no "...". The second is 100 emoji followed by "...", an emoji is never cut in two |
| With mocks | Friend notification fails | A friend chat. `sendPushToUsers` throws | `new_message` | The message is still sent. The failure is logged as "Notifying about a message in chat 7 failed:" |
| With mocks | Connections that stop answering pings are dropped | Fake timers for the check every 30 seconds. One user's app answers pings, the other's never does. Two checks pass | None | The silent connection is dropped (close code 1006). The one that answers stays open |

**How a `send_message` request is checked** (unit tests of `parseSendMessage` in `backend/src/chat/validation/sendMessage.ts`, no database)

| **Test** | **Input** | **Expected output** |
| -------- | --------- | ------------------- |
| A valid request is accepted with its text trimmed | `{ type: "send_message", chat_id: 7, text: "  Hi there  ", client_id: "c-1" }` | `{ chatId: 7, text: "Hi there", clientId: "c-1" }` |
| A client_id that isn't a string becomes null | `client_id` missing, 12, then null | `clientId: null` |
| A request that isn't a JSON object is rejected | "hello", "null", a JSON string, then a JSON number | Error "Request must be a JSON object" |
| A type other than send_message is rejected | `type` missing, then "typing" | Error "type must be send_message" |
| A chat_id that isn't a valid id is rejected | `chat_id` missing, "7" (a string), 0, -3, 1.5, then 4294967296 | Error "chat_id must be a positive whole number" |
| An empty text is rejected | `text` missing, 42, "", then only spaces and line breaks | Error "text must not be empty" |
| Text of 1000 characters is accepted, outer spaces don't count | 1000 characters with a space on each side | Accepted, without the spaces |
| Text of 1001 characters is rejected | 1001 characters | Error "text must be at most 1000 characters" |

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

**Cleaning up Docker before and after the tests (both ways)**

The database is kept in the Docker volume `mysql_data` between runs. Start and end each test run with an empty database, so a run that stopped early can't leave users behind (for example a "Duplicate entry ... uq_users_google_id" error), and the tables always match `mysql/init/`. `docker compose down -v` stops the containers and deletes the volume, so the next start creates the tables again.

- **Before the tests**, from the root folder:
  ```
  docker compose down -v
  ```
- **After the tests**, from the root folder, instead of a plain `docker compose down`:
  ```
  docker compose down -v
  ```

This deletes everything in the database, including accounts made with the app. After it, User A and User B sign up in the app again before the front-end tests (section 4.2).

**Running the tests on your computer with npm**

`docker-compose.yml` makes MySQL reachable from your own computer, on `127.0.0.1:3307` (only from that computer). Before the tests run, `backend/tests/setupEnv.ts` reads the database login from the root `.env` and connects to that port.

1. **Install the back-end packages:**
   ```
   cd backend
   npm install
   cd ..
   ```
2. **Start a fresh test database** from the root folder, see "Cleaning up Docker" above:
   ```
   docker compose down -v
   docker compose up -d mysql-db
   ```
3. **Run all the tests** from the root folder. The script runs the tests with mocks, then without mocks, then both together. Each run saves a coverage report in `backend/coverage/mock`, `backend/coverage/no-mock` and `backend/coverage/interface`.
   - macOS / Linux: `./scripts/run-backend-interface-tests.sh`
   - Windows: `.\scripts\run-backend-interface-tests.ps1`

   Or run one set of tests from the `backend` folder:
   - Tests with mocks: `npx jest tests/mock`
   - Tests without mocks: `npx jest tests/no-mock`
   - One component: `npx jest tests/no-mock/game` or `npx jest tests/mock/friend`
   - The User component's tests: `npx jest tests/mock/user tests/no-mock/user`
   - The Review component's tests: `npx jest tests/mock/review tests/no-mock/review tests/unit/review`
   - The Message component's tests: `npx jest tests/mock/chat tests/no-mock/chat tests/unit/chat`
   - One interface: `npx jest tests/no-mock/friend/searchUsers.test.ts`
4. **Stop and empty the database** from the root folder when you're done:
   ```
   docker compose down -v
   ```

**Running the tests inside Docker**

The back-end container reaches MySQL directly, so it doesn't need npm on your computer.

1. **Start a fresh MySQL and the back end** from the root folder. The container copies the code when it is built, so run the second command again after changing a test.
   ```
   docker compose down -v
   docker compose up -d --build mysql-db backend
   ```
2. **Run the tests** from the root folder:
   - Tests with mocks: `docker compose exec backend npx jest tests/mock --coverageDirectory=coverage/mock`
   - Tests without mocks: `docker compose exec backend npx jest tests/no-mock --coverageDirectory=coverage/no-mock`
   - Both together: `docker compose exec backend npx jest tests/mock tests/no-mock --coverageDirectory=coverage/interface`
   - One component or interface: `docker compose exec backend npx jest tests/no-mock/game` or `docker compose exec backend npx jest tests/no-mock/friend/searchUsers.test.ts`

   The coverage reports are saved inside the container, in `/app/coverage/`.
3. **Stop the containers and empty the database** when you're done:
   ```
   docker compose down -v
   ```

### 2.2. GitHub Actions Configuration Location

[`.github/workflows/backend-tests.yml`](../.github/workflows/backend-tests.yml)

It runs the back-end interface tests with `scripts/run-backend-interface-tests.sh`: the tests with mocks, then without mocks, then both together. It runs on every push to `main` and every pull request that changes the back end, the database scripts or the workflow, and can also be started by hand from the repository's **Actions** tab (**Backend tests** → **Run workflow**).

Each run starts its own empty MySQL 8.4 database, creates the tables from `mysql/init/`, and uses test-only database and JWT settings, so it needs no secrets. The coverage reports of the three runs are saved with the run as the `backend-coverage` artifact.

The front-end E2E tests don't run in GitHub Actions, since they need an emulator signed in with a Google account and a manual tester acting as User B (see section 4).

### 2.3. Jest Coverage Report Screenshots With Mocks

_To be added at CP2._

### 2.4. Jest Coverage Report Screenshots Without Mocks

_To be added at CP2._

---

## 3. Tests of Non-Functional Requirements

### 3.1. Test Locations in Git

| **Non-Functional Requirement** | **Location in Git** |
| ------------------------------ | ------------------- |
| **Security (only signed in users, only their own data)** | Back-end: [`backend/tests/nfr/security/everyRouteIsChecked.test.ts`](../backend/tests/nfr/security/everyRouteIsChecked.test.ts)<br>[`backend/tests/nfr/security/badSignInsAreRejected.test.ts`](../backend/tests/nfr/security/badSignInsAreRejected.test.ts)<br>[`backend/tests/nfr/security/othersGameIsProtected.test.ts`](../backend/tests/nfr/security/othersGameIsProtected.test.ts)<br>[`backend/tests/nfr/security/chatSocketIsProtected.test.ts`](../backend/tests/nfr/security/chatSocketIsProtected.test.ts)<br>Front-end: [`frontend/app/src/androidTest/java/com/example/cpen321application/nfr/SecurityTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/nfr/SecurityTest.kt) |
| **Chat Responsiveness (message delivered within 1 second)** | Back-end: [`backend/tests/nfr/chatResponsiveness.test.ts`](../backend/tests/nfr/chatResponsiveness.test.ts)<br>Front-end: [`frontend/app/src/androidTest/java/com/example/cpen321application/nfr/ChatResponsivenessTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/nfr/ChatResponsivenessTest.kt) |

### 3.2. Test Verification

- **Security (only signed in users, only their own data)**

  - **Back-end test 1, every route is checked:** makes sure the other tests miss no endpoint.
    - **The test:** It reads every route registered on the Express app, including the routers `app.ts` mounts, and compares them with the public routes (sign up, login and health) plus the list of protected routes the next test calls. They must match exactly, so a new route added without being added to the list fails this test.
    - **How to run:** start the test database (see section 2.1.3), then run `npx jest tests/nfr` in `backend/`, or `scripts/run-backend-nfr-tests`.

  - **Back-end test 2, bad sign ins are turned down:** checks every protected route turns down every kind of bad token and changes nothing.
    - **Before the test:** The test adds a victim with a game, a player in it, a chat message, a pending join request, a pending friend request, a notification and a registered device, so every route has something it could read or change. It also adds a user, makes a token for them, then deletes them.
    - **The test:** It calls every protected route (29 of them) on the victim's game, chat, requests and account, each with 6 bad sign ins. All but the last claim to be the victim, as an attacker would: no token (`400 invalid_request`), a token that isn't a JWT, a token signed with a secret that isn't the server's, and a token with no signature (`"alg": "none"`) (each `401 invalid_token`), a token the server signed that expired a minute ago (`401 expired_token`), and a token for the deleted user (`401 no_user`). Requests that change data send a real body, such as a new name or a game edit, so one that got through would change something. Every request must get the expected status and `msg`. Afterwards everything saved about the victim, their games, players, join and friend requests, chat messages, notifications and devices, must be exactly as before.
    - **After the test:** Deleting the users also deletes their game, chat and requests.
    - **How to run:** start the test database (see section 2.1.3), then run `npx jest tests/nfr` in `backend/`, or `scripts/run-backend-nfr-tests`.

  - **Back-end test 3, someone else's game is protected:** checks a signed in user can't use a game or chat they aren't in.
    - **Before the test:** The test adds an organizer with a game, a player in it, a chat message and a pending join request, and an outsider who isn't in the game.
    - **The test:** With the outsider's own valid token, it reads the game's chat messages, edits, cancels and completes the game, removes its player, lists and approves its join request, and invites to it. Each must be turned down with `403 forbidden`, and everything saved about the organizer, their game and its chat must be exactly as before.
    - **After the test:** Deleting the users also deletes the game, chat and request.
    - **How to run:** start the test database (see section 2.1.3), then run `npx jest tests/nfr` in `backend/`, or `scripts/run-backend-nfr-tests`.

  - **Back-end test 4, the chat WebSocket is protected:** checks only members can open a chat.
    - **Before the test:** The test starts the chat WebSocket server on its own port, and adds a game with a player and an outsider who isn't in it.
    - **The test:** It tries to connect to the game's chat with no token (`400`), a token that isn't a JWT, a forged token, a token with no signature and an expired token for the player (each `401`), and the outsider's valid token (`403`). Each must be turned down with that status. The player's own valid token must then connect, which shows the others were turned down for their token and not for another reason.
    - **After the test:** The connections are closed and the server is stopped.
    - **How to run:** start the test database (see section 2.1.3), then run `npx jest tests/nfr` in `backend/`, or `scripts/run-backend-nfr-tests`.

  - **Front-end test:** checks what the app does when the server turns its token down.
    - **Before the test:** The test keeps the token the app saved, if there is one, and saves a forged token instead: a JWT in the server's format with a made-up signature, as an attacker without the server's secret would make. This test doesn't need you to sign in.
    - **The test:** It opens the app, which starts on Home with the saved token and asks the server for its data. The server answers `401 invalid_token`. The test waits until the sign in screen shows "Your session ended. Please sign in again.", checks the bottom bar with the "Home" tab is gone, and checks the app cleared the forged token so it isn't used again.
    - **After the test:** The token kept before the test is saved again, so the user stays signed in for the Chat Responsiveness test.
    - **How to run:** start an emulator, then run `scripts/run-frontend-nfr-tests` and sign in when it asks (the Chat Responsiveness test needs it).

- **Chat Responsiveness (message delivered within 1 second)**

  - **Back-end test:** checks the time from a message reaching the server to it reaching another member.
    - **Before the test:** The test starts the chat WebSocket server on its own port, since test files run at the same time. It adds two users, a game organized by one of them with the other as a player, and connects both to the game's chat with their own JWT.
    - **The test:** The player sends 20 messages one after another. For each one, the test times from sending it to the organizer's connection getting it. The organizer must get a `new_message` with the same text, chat and sender, and each must arrive in under 1 second.
    - **After the test:** The connections are closed. Deleting the users also deletes the game, its chat and the messages.
    - **How to run:** start the test database (see section 2.1.3), then run `npx jest tests/nfr` in `backend/`, or `scripts/run-backend-nfr-tests`.

  - **Front-end test:** checks the same time on the phone, including sending the message and showing it.
    - **Before the test:** The user must be signed in, because Google Sign-In can't be automated. `scripts/run-frontend-nfr-tests` starts the back-end and the app, then waits while you sign in on the emulator. The test creates a game organized by the user through the app's own `createGame`, starting in 1 hour, so the user is in its chat. It then opens the app.
    - **The test:** It finds the game "NFR Chat Test" on the Home screen, opens it, and taps "Open Chat". It waits until the chat is connected, when "Connecting..." and the loading bar are gone. Then it sends 20 messages one after another. The app shows each message as "Sending..." until the server sends it back, which the server does to every member with the chat open at the same time. So for each message the test types it, taps Send and starts the clock, then waits until the message is shown and "Sending..." is gone. If that takes over 1 second, the test fails. The times are written to the log with the tag `NFR2`.
    - **After the test:** The test cancels the game, so it doesn't stay on the user's Home screen.
    - **How to run:** start an emulator, then run `scripts/run-frontend-nfr-tests` and sign in when it asks. This runs both front-end NFR tests.

---

## 4. Front-end Test Specification

### 4.1. Location in Git of Front-end Test Suite:

`frontend/app/src/androidTest/java/com/example/cpen321application/e2e/`

| **Feature** | **Test file** |
| ----------- | ------------- |
| Friend System | [`FriendSystemTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/e2e/FriendSystemTest.kt) |
| Game Discovery | [`DiscoverGameTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/e2e/DiscoverGameTest.kt), [`JoinGameTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/e2e/JoinGameTest.kt), [`MyGamesTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/e2e/MyGamesTest.kt) |
| Game Planning | [`CreateGameTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/e2e/CreateGameTest.kt), [`GamePlanningTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/e2e/GamePlanningTest.kt) |
| Chat (game chat) | [`GameChatTest.kt`](../frontend/app/src/androidTest/java/com/example/cpen321application/e2e/GameChatTest.kt) |

The game discovery and game chat tests share helpers in [`androidTest/.../testutil/`](../frontend/app/src/androidTest/java/com/example/cpen321application/testutil/): `TestLocation.kt` sets the phone's location, `TestOrganizer.kt` acts as the test games' organizer through the back end, `UserB.kt` waits for User B's steps, and `ComposeWaits.kt` waits for the screen. Their test data comes from [`backend/tests/e2e/seedGameDiscovery.ts`](../backend/tests/e2e/seedGameDiscovery.ts), see [Test data for the game discovery and game chat tests](#test-data-for-the-game-discovery-and-game-chat-tests). The game planning tests also use `ComposeWaits.kt`, and `ToastWaits.kt` checks the app's toasts. The same seed script saves their test data, see [Test data for the game planning tests](#test-data-for-the-game-planning-tests). The helpers are kept out of the `e2e/` folder because `scripts/run-frontend-e2e-tests` runs every file in that folder as a test class.

### 4.2. Tests

#### Setting up User B's emulator

The tests that involve two users need the manual tester's User B account, "Bea Tester", on a second device. A real phone works. These steps set up a second emulator on the same computer as User A's emulator. Steps 1 to 5 are needed only once, since the emulator keeps its Google account, the app and its sign-in between runs.

1. **Create the emulator.** In Android Studio, open **Device Manager** and click **+** > **Create Virtual Device**. Pick a phone (for example Pixel 9) and a system image whose name includes **Google Play**, API 33 or newer, since the app needs Google sign-in and push notifications. Name it so it's easy to tell apart, for example `User_B`. It must be a different virtual device from User A's, since one virtual device can only run once at a time.
2. **Start both emulators.** Click ▶ next to User A's emulator and then next to `User_B` in Device Manager. Check that both are connected:

   PowerShell:
   ```powershell
   adb devices
   ```
   Bash:
   ```bash
   adb devices
   ```
   Each emulator is listed with its serial, for example `emulator-5554` and `emulator-5556`. To tell which is which, see [Running the tests on User A's device only](#running-the-tests-on-user-as-device-only).
3. **Add a Google account.** On `User_B`, open **Settings** > **Google** (or **Passwords & accounts**) and sign in with a Google account that isn't User A's.
4. **Install the app on `User_B`.** Build the app from the `frontend` folder, then install it with User B's serial. The app's back-end address (`API_BASE_URL` in `frontend/local.properties`, for example `http://10.0.2.2:3000`) is the same for every emulator, so both use the same back end.

   PowerShell:
   ```powershell
   cd frontend
   .\gradlew.bat :app:assembleDebug
   adb -s emulator-5556 install -r app\build\outputs\apk\debug\app-debug.apk
   ```
   Bash:
   ```bash
   cd frontend
   ./gradlew :app:assembleDebug
   adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. **Sign up as User B.** Open the app on `User_B`, sign in with the Google account from step 3, and sign up with the first name **Bea** and the last name **Tester**. No other user may have this name. When Android asks whether the app may send notifications, click **Allow**, since some User B steps check a notification arrived.
6. **Before each run:**
   - Start both emulators, and keep the app open on `User_B`'s main screen.
   - Check that User A and User B aren't friends and have no pending friend requests. `FriendSystemTest` leaves them that way, but a failed run may not: on either device, remove Bea Tester or User A from **Friends**, and decline any request under **Notifications** > **Friend requests**.
   - For `GameChatTest`, User B must have signed up before the test data is saved, so the seed script can add them to "E2E Chat", see [Test data for the game discovery and game chat tests](#test-data-for-the-game-discovery-and-game-chat-tests).
   - Set `ANDROID_SERIAL` to User A's emulator, see the next section, and start the User B helper, see [Doing User B's steps](#doing-user-bs-steps).

#### Cleaning up Docker before and after the tests

The front-end tests use the same Docker database as the app, which keeps its data in the `mysql_data` volume between runs. `scripts/run-frontend-e2e-tests` starts the back end and its database itself.

- **Before the tests**, from the root folder, start from an empty database, so no earlier run's friendships, requests or test games are left:
  ```
  docker compose down -v
  ```
  This deletes every account too, so the run script's sign-in step is done as a new user: User A signs up again on their emulator, and User B signs up again as **Bea** **Tester** on theirs (step 5 of [Setting up User B's emulator](#setting-up-user-bs-emulator)) before the tests start. To keep the accounts between runs instead, skip this step and only remove the test data, as below.
- **After the tests**, from the root folder, remove the test games and the test organizer that `backend/tests/e2e/seedGameDiscovery.ts` saved. Deleting the organizer also deletes their games' players, chats, messages, join requests and notifications. The command reads the database login from the MySQL container, so no password is typed:

  PowerShell:
  ```powershell
  "DELETE FROM users WHERE google_id = 'e2e-test-organizer'; DELETE FROM games WHERE title LIKE 'E2E %';" | docker compose exec -T mysql-db sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
  ```
  Bash:
  ```bash
  echo "DELETE FROM users WHERE google_id = 'e2e-test-organizer'; DELETE FROM games WHERE title LIKE 'E2E %';" | docker compose exec -T mysql-db sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
  ```
  Then stop the containers with `docker compose down`, or with `docker compose down -v` to empty the database completely.

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

#### Test data for the game discovery and game chat tests

`DiscoverGameTest`, `JoinGameTest`, `MyGamesTest` and `GameChatTest` need games that User A didn't organize, since Discover never shows a user their own games. So `scripts/run-frontend-e2e-tests` saves them before the tests:

1. Once you have signed in to the app and pressed Enter, the script asks for the email of the Google account you signed in with. To skip the question, set it first: `$env:E2E_USER_EMAIL = "you@example.com"` (PowerShell) or `export E2E_USER_EMAIL=you@example.com` (Bash).
2. It runs `backend/tests/e2e/seedGameDiscovery.ts` inside the backend container (`docker compose exec -T backend npx tsx tests/e2e/seedGameDiscovery.ts <email>`). The script first removes what an earlier run saved, so every run starts from the same games. It then saves a test organizer, "Olive Organizer", and these games:

   | **Game** | **Sport** | **Where** | **Used by** |
   | -------- | --------- | --------- | ----------- |
   | "E2E Basketball Near" | basketball | 200 m from the Discover spot | `DiscoverGameTest` |
   | "E2E Basketball Far" | basketball | 1 km from the Discover spot | `DiscoverGameTest` |
   | "E2E Soccer" | soccer | 2 km from the Discover spot | `DiscoverGameTest` |
   | "E2E Join", "E2E Join Cancelled", "E2E Join Started", "E2E Join Offline" | basketball | near the games spot | one `JoinGameTest` test each |
   | "E2E Leave" | soccer | near the games spot, User A has joined it | `MyGamesTest` (Leave Game) |
   | "E2E Chat" | volleyball | near the games spot, User A and User B have joined it, with the messages "E2E hello from the organizer" and "E2E bring a ball please" from the organizer | `MyGamesTest`, `GameChatTest` |
   | "E2E Organized" | tennis | organized by User A | `MyGamesTest` (View Upcoming Games) |

   There are no volleyball games near the Discover spot. The Discover spot (45° N, 135° W) and the games spot (46° N, 135° W) are in the Pacific Ocean, about 111 km apart, so no real game is within Discover's 50 km of either, and the games near one don't show at the other. User B is added to "E2E Chat" only if the account "Bea Tester" exists (see [Doing User B's steps](#doing-user-bs-steps)); otherwise the script says so and only Receive Message fails.
3. The seed script prints a token that signs in as the test organizer. The runner passes it to the tests as the `organizerToken` instrumentation argument, and `TestOrganizer` uses it to cancel or start a game while User A has its join request form open (Join Game 4a and 4b), and to read the game chat.

Every game starts on a different day, 1 to 10 days after the seed runs, so the lists are always in the same order. The tests also need:

- **Phone location:** `TestLocation` replaces the phone's fused, network and GPS location providers with test providers that report the test spot, after allowing the app to use them (`appops set ... android:mock_location allow`). It gives the app location permission first (`grantRuntimePermission`), so Android's permission pop-up never shows, and removes the test providers after each test.
- **Location off:** `cmd location set-location-enabled false` turns the phone's location off, and it's turned back on after the test.
- **No connection:** `cmd connectivity airplane-mode enable` cuts the phone off from the server. Airplane mode is turned off after the test, waiting for the phone to reconnect.

#### Test data for the game planning tests

`CreateGameTest` and `GamePlanningTest` work on User A's own games. The same seed run, `backend/tests/e2e/seedGameDiscovery.ts` (see [Test data for the game discovery and game chat tests](#test-data-for-the-game-discovery-and-game-chat-tests)), first removes User A's games whose titles start with "E2E ", including the ones `CreateGameTest` made in an earlier run, and the test users below. It then saves:

| **Saved** | **Used by** |
| --------- | ----------- |
| "E2E Edit", with only User A in it | `GamePlanningTest.editGame_savesTheChanges` |
| "E2E Cancel", with only User A in it | `GamePlanningTest.cancelGame_marksTheGameCancelled` |
| "E2E Invite", with only User A in it, and User A's friends "Fiona Friend" and "Finn Friend" | `GamePlanningTest.inviteFriends_sendsTheInvite` |
| "E2E Accept", with a pending join request from "Alex Asker": Intermediate, plays weekly, "Happy to play any position!" | `GamePlanningTest.acceptJoinRequest_addsThePlayer` |
| "E2E Reject", with a pending join request from "Blake Asker": Beginner, plays rarely, "First time playing, hope that is okay." | `GamePlanningTest.rejectJoinRequest_declinesTheRequest` |
| "E2E Remove", with "Riley Player" in it | `GamePlanningTest.removePlayer_removesThePlayer` |

Every game is organized by User A, has room for 10 players and starts 11 to 16 days after the seed runs, after "E2E Organized". The friendships and join requests are saved by the back end's own functions (`sendFriendRequest`, `decideFriendRequest` and `createJoinRequest`), as the app saves them. The test users' Google ids start with `e2e-test-`, which no real account has. `CreateGameTest` only uses "E2E Organized", to know Home has loaded. The tests change these games, so they need a fresh seed for each run, which `scripts/run-frontend-e2e-tests` always does.

The game planning tests also need:

- **Emulator:** a Google Play or Google APIs system image, which can look up addresses, with the language set to English (United States). The tests type dates as MM/DD/YYYY and pick times by the clock's English labels.
- **Location permission:** `CreateGameTest` gives the app location permission before each test (`grantRuntimePermission`), since the map asks for it when it opens.
- **No connection:** `cmd connectivity airplane-mode enable`, as in `JoinGameTest`. Without the network, the phone can't look up addresses (Create Game 6a) or reach the server (Create Game 8a). Airplane mode is turned off after each test, waiting for the phone to reconnect.
- **Map:** the map is drawn by Google Maps, not Compose, so the tests drag it with UI Automator (`UiDevice.swipe`).
- **Toasts:** the app shows "Game created", "Couldn't reach the server, try again" and "Invite sent" as toasts, which Android draws outside the app's Compose screens. `ToastWaits.kt` checks them through the accessibility event Android sends for each toast (`UiAutomation.executeAndWaitForEvent`).

#### Use cases

The tests in `FriendSystemTest` run in a fixed order, by their names (`test1…`, `test2…`, `test3…`). Each one leaves User A and User B with no friendship and no requests: `test3AcceptFriendRequestAndChat` makes them friends and ends by removing the friend again.

The game discovery and game chat tests (`DiscoverGameTest`, `JoinGameTest`, `MyGamesTest` and `GameChatTest`) can run in any order: each test has its own game, or only reads games no other test changes. So can the game planning tests (`CreateGameTest` and `GamePlanningTest`): each `GamePlanningTest` test has its own game, and each `CreateGameTest` test makes a game with a new title.

- **Use Case: Create Game (Use Case 17, formal Use Case 3, tests in `CreateGameTest`)**

  The scenario steps come from the formal specification (Requirements and Design section 3.5, Use Case 3). Each test opens the form the same way: click the "Home" tab, wait until the "E2E Organized" card is on the screen so Home has loaded, click the button "Organize a new game", and wait until "Sport *" is on the screen. Each game a test makes gets a new title, "E2E" and the time, such as "E2E New Game 143052".

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the tests, on the main screen.
    - **Test data:** "E2E Organized", see [Test data for the game planning tests](#test-data-for-the-game-planning-tests).
    - **Location permission:** given by the test before it starts.

  - **Expected Behaviors, main success scenario (`createGame_createsTheGameAndShowsItOnHome`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Game Organiser selects “Create game”. | Open the form. |
    | 2. System displays the game creation form with fields for the sport, title, date, time, max players, skill level, description (optional), and a map for selecting the location. | Check that "Sport \*", "Title \*", "Date \*", "Time \*", "Location \*", "Max players", "Skill level" and "Description (optional)" are on the screen.<br>Check that "Choose a location" is on the screen. |
    | 3. Game Organiser fills in all the details and clicks on the map. | Click the "Basketball" chip.<br>Input "E2E New Game &lt;time&gt;" in the title field.<br>Click "Select date", click the button "Switch to text input mode", input the date in 3 days and click the button "OK".<br>Click "Select time" and click the button "OK", keeping 6:00 PM.<br>Click the button "More players", for 11 players.<br>Click the "Intermediate" chip.<br>Input "Bring water, we play two halves." in the description field.<br>Click "Choose a location". |
    | 4. System opens the Map API. | Wait until "SELECTED LOCATION" is on the screen.<br>Check that "Drag the map to move the pin, or search above." is on the screen. |
    | 5. Game Organiser selects the game location on the map. | Drag the map a little to the left with UI Automator, so the pin is over another place. |
    | 6. System determines and displays the address of the selected location, then closes the map. | Wait until the button "Confirm Location" is enabled, which happens once the phone has looked up the place under the pin.<br>Check that "Dropped pin" is not on the screen, so an address was found.<br>Click the button "Confirm Location".<br>Wait until "Change" is on the screen, and check that "Choose a location" is not. |
    | 7. Game Organiser clicks “Create game”. | Check that the button "Create Game" is enabled, and click it. |
    | 8. System creates the game and displays a confirmation message. | Check that the toast "Game created" is shown.<br>Wait until Home has the card "E2E New Game &lt;time&gt;" showing "1/11". |

  - **4a, System cannot connect to the Map API: not tested.** The map and the address lookup reach Google over the same network as the rest of the phone, so a test can't cut off the Map API alone. With the whole network off, the address can't be looked up, which the 6a test checks.

  - **Expected Behaviors, 6a (`createGame_noAddress_usesDroppedPin`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1–5. Game Organiser opens the form, fills it in and clicks on the map. | Open the form.<br>Click the "Basketball" chip, input a title, and pick the date in 3 days and 6:00 PM.<br>Turn airplane mode on.<br>Click "Choose a location". |
    | 6a. The Map API cannot determine an address for the selected location. | (In airplane mode, the phone can't look up an address.) |
    | 6a1. System shows the location as "Dropped pin", with its coordinates in place of the address. The Game Organiser can confirm it or move the pin to another spot. | Wait until "Dropped pin" is on the screen.<br>Check that the map's starting point, "49.26060, -123.24600", is on the screen.<br>Check that the button "Confirm Location" is enabled, and click it.<br>Wait until "Change" is on the screen, and check that "Dropped pin" is on the form.<br>Check that the button "Create Game" is enabled. |

  - **Expected Behaviors, 7a (`createGame_missingField_keepsCreateDisabled`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1–2. Game Organiser selects “Create game” and the system displays the form. | Open the form. |
    | 7a. Game Organiser left a compulsory field empty or did not select a location. | (Every field is empty.) |
    | 7a1. System keeps the "Create game" button disabled until every compulsory field, marked with \*, is filled in and a location is selected. | Check that "Sport \*", "Title \*", "Date \*", "Time \*" and "Location \*" are on the screen, and the button "Create Game" is disabled.<br>Click the "Basketball" chip, input a title, and pick the date in 3 days and 6:00 PM, leaving the location empty.<br>Check that the button "Create Game" is still disabled.<br>Click "Choose a location", wait until the button "Confirm Location" is enabled and click it.<br>Check that the button "Create Game" is enabled.<br>Clear the title field, and check that the button "Create Game" is disabled again. |

  - **Expected Behaviors, 7b (`createGame_startInThePast_showsError`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1–6. Game Organiser opens the form and fills it in. | Open the form.<br>Click the "Basketball" chip and input a title.<br>Pick today's date.<br>Click "Select time", click "12 o'clock", then the button "AM", then the button "OK", for 12:00 AM.<br>Choose a location. |
    | 7b. Game Organiser entered an invalid value in any field. | (12:00 AM today has already passed. The form doesn't let any other field get an invalid value: the max players buttons stop at 2 and 100, the title at 100 characters, the description at 1000, and the date picker only allows today or later.) |
    | 7b1. System displays an error message identifying the invalid field and the reason. | Check that "12:00 AM" and "The game has to start in the future." are on the screen.<br>Check that the button "Create Game" is disabled. Every field is filled in, so the start time is the only problem. |

  - **Expected Behaviors, 8a (`createGame_noConnection_showsErrorAndTryAgain`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1–7. Game Organiser fills in the form and clicks “Create game”. | Open the form.<br>Click the "Basketball" chip, input "E2E Offline Game &lt;time&gt;" as the title, pick the date in 3 days and 6:00 PM, and choose a location.<br>Turn airplane mode on.<br>Click the button "Create Game". |
    | 8a. The phone cannot reach the server (e.g. no internet connection). | (Airplane mode is on.) |
    | 8a1. System displays "Couldn't reach the server, try again" and lets the Game Organiser try again. | Check that the toast "Couldn't reach the server, try again" is shown.<br>Wait until the button "Create Game" is enabled again, and check that the title field still has the title.<br>Turn airplane mode off and wait for the phone to reconnect.<br>Click the button "Create Game".<br>Check that the toast "Game created" is shown, and wait until Home has the game's card. |

  - **8b, The server fails while creating the game: not tested.** The server only fails like this when its database does. An E2E test uses the real back end with valid input, so it can't make the database fail. The back-end test with mocks makes `createGame` throw and checks the `500 server_error` reply (`POST /api/games`, section 2.1), and for that reply the app shows "Could not create game, try again" and keeps the form.

  - **Test Logs:**
    ```
    To be added once the tests pass on User A's emulator.
    ```

- **Use Case: Edit Game (Use Case 18, test `GamePlanningTest.editGame_savesTheChanges`)**

  Edit Game has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does. Each `GamePlanningTest` test opens its game the same way: click the "Home" tab, scroll Home's list until the game's card is on the screen, click it, and wait until "Organized by you" is on the screen.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **Test data:** "E2E Edit", with only User A in it and room for 10 players, see [Test data for the game planning tests](#test-data-for-the-game-planning-tests).

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Organiser opens their game and selects "Edit". | Open "E2E Edit".<br>Click the button "Edit". |
    | 2. System shows the form filled in with the game's details, with nothing to save yet. | Wait until the title field has "E2E Edit".<br>Check that "Edit Game" is on the screen.<br>Check that the button "Save Changes" is disabled. |
    | 3. Organiser changes the details and saves. | Replace the title with "E2E Edited".<br>Click the button "More players", from 10 to 11 players.<br>Click the "Intermediate" chip.<br>Check that the button "Save Changes" is enabled, and click it. |
    | 4. System saves the changes and shows the game's details with them. | Wait until "Players · 1/11" is on the screen.<br>Check that "E2E Edited" and "Intermediate" are on the screen.<br>Check that "E2E Edit" is not on the screen. |

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Case: Cancel Game (Use Case 19, test `GamePlanningTest.cancelGame_marksTheGameCancelled`)**

  Cancel Game has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **Test data:** "E2E Cancel", see [Test data for the game planning tests](#test-data-for-the-game-planning-tests).

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Organiser opens their game and selects "Cancel Game". | Open "E2E Cancel".<br>Click the button "Cancel Game". |
    | 2. System asks the organiser to confirm, saying the players will be told. | Wait until "Cancel this game?" is on the screen.<br>Check that "E2E Cancel will be cancelled for everyone" is on the screen. |
    | 3. Organiser confirms. | Click the button "Cancel Game" in the dialog. |
    | 4. System cancels the game and notifies its players. The game can't be changed any more. | Wait until "This game was cancelled." is on the screen.<br>Check that the buttons "Edit", "Cancel Game" and "Invite Friends" are not on the screen. |

    The players' notifications can't be seen on User A's phone. The back-end tests check who is notified (`POST /api/games/{gameId}/cancel`, section 2.1).

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Case: Invite Friends (Use Case 20, test `GamePlanningTest.inviteFriends_sendsTheInvite`)**

  Invite Friends has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **Test data:** "E2E Invite", and User A's friends "Fiona Friend" and "Finn Friend", see [Test data for the game planning tests](#test-data-for-the-game-planning-tests).

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Organiser opens their game and selects "Invite Friends". | Open "E2E Invite".<br>Click the button "Invite Friends". |
    | 2. System lists the organiser's friends who aren't in the game. | Wait until "Fiona Friend" is in the list, and check that "Finn Friend" is too.<br>Check that the button "Invite" is disabled. |
    | 3. Organiser selects one or more friends. | Click "Fiona Friend", and check that her row is ticked. |
    | 4. Organiser selects "Invite", and the system sends each friend a notification. | Click the button "Invite (1)".<br>Check that the toast "Invite sent" is shown.<br>Wait until the list is closed. |

    Fiona has no device, so her notification can't be seen. The back-end tests check the notification each invite saves and sends (`POST /api/games/{gameId}/invites`, section 2.1).

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Case: Accept Join Request (Use Case 21, test `GamePlanningTest.acceptJoinRequest_addsThePlayer`)**

  Accept Join Request has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **Test data:** "E2E Accept", with a pending join request from "Alex Asker", see [Test data for the game planning tests](#test-data-for-the-game-planning-tests).

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Organiser opens a join request from their game. | Open "E2E Accept".<br>Click "1 pending join request".<br>Wait until "Join Requests" is on the screen. |
    | 2. System shows the request with the player's rating, how often they play and their message. | Wait until "Alex Asker" is on the screen.<br>Check that "Not rated yet · Intermediate · Plays weekly" and "Happy to play any position!" are on the screen.<br>Check that "1/10 players" is on the screen. |
    | 3. Organiser accepts it. | Click the button "Approve". |
    | 4. System adds the player to the game and notifies them. | Wait until "Alex Asker" is not on the screen.<br>Check that "No join requests right now. You'll get a notification when someone asks to join." and "2/10 players" are on the screen.<br>Click the button "Back", and wait until "Players · 2/10" is on the screen.<br>Click "See all", and check that "Alex Asker" is in the list of players. |

    Badges aren't built yet, so the request shows the player's rating, skill level and how often they play. The seed script saves Alex's request straight to the database, so no notification is sent for it, and the test opens it from the game. Alex's notification is checked by the back-end tests (`PATCH /api/games/{gameId}/join-requests/{requestId}`, section 2.1).

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Case: Reject Join Request (Use Case 22, test `GamePlanningTest.rejectJoinRequest_declinesTheRequest`)**

  Reject Join Request has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **Test data:** "E2E Reject", with a pending join request from "Blake Asker", see [Test data for the game planning tests](#test-data-for-the-game-planning-tests).

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Organiser opens a join request from their game. | Open "E2E Reject".<br>Click "1 pending join request".<br>Wait until "Join Requests" is on the screen. |
    | 2. System shows the request with the player's rating, how often they play and their message. | Wait until "Blake Asker" is on the screen.<br>Check that "Not rated yet · Beginner · Plays rarely" and "First time playing, hope that is okay." are on the screen. |
    | 3. Organiser rejects it. | Click the button "Decline". |
    | 4. System declines the request and notifies the player, who isn't added to the game. | Wait until "Blake Asker" is not on the screen.<br>Check that "No join requests right now. You'll get a notification when someone asks to join." and "1/10 players" are on the screen.<br>Click the button "Back", wait until "Players · 1/10" is on the screen, and wait until "1 pending join request" is not.<br>Click "See all", and check that "Blake Asker" is not in the list of players. |

    Blake's notification is checked by the back-end tests (`PATCH /api/games/{gameId}/join-requests/{requestId}`, section 2.1).

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Case: Remove Player (Use Case 23, test `GamePlanningTest.removePlayer_removesThePlayer`)**

  Remove Player has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **Test data:** "E2E Remove", with "Riley Player" in it, see [Test data for the game planning tests](#test-data-for-the-game-planning-tests).

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Organiser opens their game's list of players. | Open "E2E Remove".<br>Wait until "Players · 2/10" is on the screen.<br>Click "See all", and check that "Riley Player" is in the list. |
    | 2. Organiser selects "Remove" next to the player. | Click the button "Remove". |
    | 3. System asks the organiser to confirm. | Wait until "Remove Riley Player?" is on the screen. |
    | 4. Organiser confirms. | Click the button "Remove" in that dialog. |
    | 5. System removes the player from the game. | Wait until "Players · 1/10" is on the screen, and until "Riley Player" is not.<br>Click the button "Close", and check that "Players · 1/10" is on the screen. |

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Case: Discover Game (Use Case 24, formal Use Case 4, tests in `DiscoverGameTest`)**

  The scenario steps come from the formal specification (Requirements and Design section 3.5, Use Case 4). Each of the three tests starts on the main screen.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the tests, on the main screen.
    - **Test data:** "E2E Basketball Near", "E2E Basketball Far" and "E2E Soccer" near the Discover spot, and no volleyball games, see [Test data for the game discovery and game chat tests](#test-data-for-the-game-discovery-and-game-chat-tests).
    - **Phone location:** set by `TestLocation`, which also gives the app location permission.

  - **Expected Behaviors, main success scenario and 5a (`discoverGame_showsNearbyGamesAndFiltersBySport`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Player selects the compass in the navigation bar. | Set the phone's location to the Discover spot.<br>Click the "Discover" tab in the bottom bar.<br>Check that the title "Discover" is on the screen. |
    | 2. System gets the Player's location from the phone. | Wait until "3 games nearby" is on the screen.<br>Check that "Showing games near" and "Your location" are on the screen.<br>Check that neither location error message is on the screen. |
    | 3. System displays the nearby games sorted by distance with their information, and a selector bar at the top with various sports. | Check that "Sorted by distance" is on the screen.<br>Check that the cards "1. E2E Basketball Near" showing "200 m", "2. E2E Basketball Far" showing "1.0 km" and "3. E2E Soccer" showing "2.0 km" are on the screen.<br>Check that the "All" chip is selected, and the "Basketball", "Soccer" and "Volleyball" chips are on the screen and not selected. |
    | 4. Player selects a sport in the selector bar. | Click the "Basketball" chip. |
    | 5. System updates the list to show only the games of the selected sport. | Wait until "2 basketball games nearby" is on the screen.<br>Check that the "Basketball" chip is selected.<br>Check that the cards "1. E2E Basketball Near" and "2. E2E Basketball Far" are on the screen.<br>Check that "E2E Soccer" is not on the screen. |
    | 5a. No nearby games are found for the selected sport. | Click the "Volleyball" chip. |
    | 5a1. System displays a message saying no games were found of the selected sport. | Wait until "No volleyball games nearby. Try another sport." is on the screen.<br>Check that none of the three games is on the screen. |

  - **Expected Behaviors, 2a (`discoverGame_locationOff_showsErrorAndTryAgain`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Player selects the compass in the navigation bar. | Turn the phone's location off.<br>Click the "Discover" tab in the bottom bar. |
    | 2a. System cannot get the Player's location (e.g. location is turned off or location access is denied). | (Location is off.) |
    | 2a1. System displays an error message and lets the Player try again or choose a place on the map. | Wait until "Location is turned off. Turn it on in your phone's settings, then try again." is on the screen.<br>Check that the button "Try again" is on the screen.<br>Check that "Choose a location" and "Change" are on the screen.<br>Check that none of the three games is on the screen.<br>Turn the phone's location back on and set it to the Discover spot.<br>Click the button "Try again".<br>Wait until "3 games nearby" is on the screen.<br>Check that neither location error message is on the screen.<br>Check that the card "1. E2E Basketball Near" showing "200 m" is on the screen. |

  - **Expected Behaviors, 3a (`discoverGame_noGamesNearby_showsMessage`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Player selects the compass in the navigation bar. | Set the phone's location to a spot about 390 km from the test games (45° N, 130° W).<br>Click the "Discover" tab in the bottom bar. |
    | 3a. No games are found nearby. | (No game is within 50 km.) |
    | 3a1. System displays a message saying no nearby games were found. | Wait until "No games nearby right now. Check back later or organize your own game." is on the screen.<br>Check that neither location error message is on the screen.<br>Check that none of the three games is on the screen. |

  - **Test Logs:**
    ```
    To be added once the tests pass on User A's emulator.
    ```

- **Use Case: Join Game (Use Case 25, formal Use Case 5, tests in `JoinGameTest`)**

  The scenario steps come from the formal specification (Requirements and Design section 3.5, Use Case 5). Its precondition, viewing the details of a game the Player hasn't joined, is reached the same way in every test: set the phone's location to the games spot, click the "Discover" tab, wait until "Sorted by distance" is on the screen, click the test's game card (found by the end of its text, such as ". E2E Join Cancelled"), and check that the button "Request to Join" is enabled. Each test has its own game, so none depends on another.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the tests, on the main screen.
    - **Test data:** "E2E Join", "E2E Join Cancelled", "E2E Join Started" and "E2E Join Offline", which User A has never asked to join, and the test organizer's token, see [Test data for the game discovery and game chat tests](#test-data-for-the-game-discovery-and-game-chat-tests). The 4a and 4b tests change their games, so the tests need a fresh seed for each run, which `scripts/run-frontend-e2e-tests` always does.

  - **Expected Behaviors, main success scenario and 3a (`joinGame_sendsRequestAndShowsItPending`, game "E2E Join"):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Player selects "Request to join". | Open "E2E Join" from Discover.<br>Click the button "Request to Join". |
    | 2. System displays the join request form with fields for skill level, how often they play (optional), and a message (optional). | Wait until the title "Request to join" is on the screen.<br>Check that "What is your skill level in basketball? *", "How often do you play?" and "Message to organizer (optional)" are on the screen. |
    | 3a. Player did not select a skill level. | (No skill level is selected yet.) |
    | 3a1. System marks the skill level as required and keeps "Send request" disabled until the Player selects it. | Check that the button "Send Request" is disabled. |
    | 3. Player fills in the form and clicks "Send request". | Click the "Intermediate" chip and the "Monthly" chip.<br>Input "E2E happy to play any position" in the message field.<br>Check that the button "Send Request" is enabled, and click it. |
    | 4. System sends the request to the organiser and shows the request as pending. | Wait until Discover shows the "E2E Join" card with "Request pending".<br>Click the "E2E Join" card.<br>Check that the button "Request sent" is on the screen and disabled.<br>Check that "Waiting for the organizer to respond. You will be notified when they do." is on the screen. |

  - **Expected Behaviors, 4a (`joinGame_gameCancelled_showsErrorAndSendsNothing`, game "E2E Join Cancelled"):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1–3. Player opens the form, fills it in and clicks "Send request". | Open "E2E Join Cancelled" from Discover.<br>Click the button "Request to Join", then the "Beginner" chip. |
    | 4a. The organiser cancelled the game before the request was sent. | As the test organizer, cancel "E2E Join Cancelled" (`POST /api/games/{gameId}/cancel`).<br>Click the button "Send Request". |
    | 4a1. System displays "This game was cancelled" and does not send the request. | Wait until "This game was cancelled" is on the screen.<br>Check that the form is still open (the title "Request to join" is on the screen) and the button "Send Request" is enabled. |

  - **Expected Behaviors, 4b (`joinGame_gameStarted_showsErrorAndSendsNothing`, game "E2E Join Started"):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1–3. Player opens the form, fills it in and clicks "Send request". | Open "E2E Join Started" from Discover.<br>Click the button "Request to Join", then the "Beginner" chip. |
    | 4b. The game started before the request was sent. | As the test organizer, edit "E2E Join Started" to start 30 seconds ago (`PUT /api/games/{gameId}`; the server accepts start times up to a minute in the past).<br>Click the button "Send Request". |
    | 4b1. System displays "This game has already started" and does not send the request. | Wait until "This game has already started" is on the screen.<br>Check that the form is still open and the button "Send Request" is enabled. |

  - **Expected Behaviors, 4c (`joinGame_noConnection_showsErrorAndTryAgain`, game "E2E Join Offline"):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1–3. Player opens the form, fills it in and clicks "Send request". | Open "E2E Join Offline" from Discover.<br>Click the button "Request to Join", then the "Beginner" chip. |
    | 4c. The phone cannot reach the server (e.g. no internet connection). | Turn airplane mode on.<br>Click the button "Send Request". |
    | 4c1. System displays "Couldn't reach the server, try again" and lets the Player try again. | Wait until "Couldn't reach the server, try again" is on the screen.<br>Check that the form is still open and the button "Send Request" is enabled.<br>Turn airplane mode off and wait for the phone to reconnect.<br>Click the button "Send Request".<br>Wait until Discover shows the "E2E Join Offline" card with "Request pending". |

  - **Test Logs:**
    ```
    To be added once the tests pass on User A's emulator.
    ```

- **Use Case: View Upcoming Games (Use Case 27, test `MyGamesTest.viewUpcomingGames_showsOrganizedAndJoinedGames`)**

  View Upcoming Games has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **Test data:** "E2E Organized", organized by User A, and "E2E Chat", joined by User A, see [Test data for the game discovery and game chat tests](#test-data-for-the-game-discovery-and-game-chat-tests). User A's own games may also be listed: the test only looks for these two.

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. User opens the Home screen. | Click the "Home" tab in the bottom bar. |
    | 2. System lists the games the user joined and the games they organise, each in its own section. | Wait until the "E2E Chat" card is on the screen.<br>Check that "Joined Games" is on the screen.<br>Check that the "E2E Chat" card and the "E2E Organized" card showing "1/10" are on the screen.<br>Click the button "Collapse Joined Games".<br>Check that the "E2E Chat" card is not on the screen and the "E2E Organized" card is.<br>Click the button "Expand Joined Games".<br>Click the button "Collapse Organized Games".<br>Check that the "E2E Organized" card is not on the screen and the "E2E Chat" card is.<br>Click the button "Expand Organized Games". |
    | 3. User selects a game. | Click the "E2E Organized" card. |
    | 4. System opens the game's details. | Wait until "Organized by you" is on the screen.<br>Check that "E2E Organized" is on the screen. |

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Case: Leave Game (Use Case 26, test `MyGamesTest.leaveGame_removesTheGameFromJoinedGames`)**

  Leave Game has no formal use case specification in Requirements and Design section 3.5, so the scenario steps below describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **Test data:** "E2E Leave", which User A has joined and which hasn't started, see [Test data for the game discovery and game chat tests](#test-data-for-the-game-discovery-and-game-chat-tests).

  - **Expected Behaviors:**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Player opens a game they joined. | Click the "Home" tab in the bottom bar.<br>Wait until the "E2E Leave" card is on the screen, and click it. |
    | 2. Player selects "Leave Game". | Click the button "Leave Game". |
    | 3. System asks the Player to confirm. | Check that "Leave this game?" and "You'll give up your spot in E2E Leave and the organizer will be notified. To play again you'll need to send a new join request." are on the screen. |
    | 3a. Player chooses "Stay". | Click the button "Stay" in the dialog. |
    | 3a1. System closes the dialog and the Player stays in the game. | Check that "Leave this game?" is not on the screen.<br>Check that the button "Leave Game" is on the screen. |
    | 4. Player confirms. | Click the button "Leave Game", then the button "Leave Game" in the dialog. |
    | 5. System removes the Player from the game, notifies the organiser, and goes back to Home, where the game is no longer listed. | Wait until "Joined Games" is on the screen.<br>Wait until the "E2E Leave" card is not on the screen.<br>Check that the "E2E Chat" card is on the screen. |

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```

- **Use Cases: Send Message (5, formal Use Case 1), View Messages (6) and Receive Message (7) in a game chat, tests in `GameChatTest`**

  These use cases cover both friend chat and game chat. `FriendSystemTest` above tests them in a friend chat, including formal Use Case 1's failures 5a and 6a; these tests check the same use cases in a game's chat room. Use Case 1's scenario is written for a friend chat, so the steps below say what a game chat does in its place. Each test opens the chat the same way: click the "Home" tab, click the "E2E Chat" card, click the button "Open Chat", wait until "E2E bring a ball please" is on the screen and "Connecting..." isn't, and check that the text field "Message" is on the screen.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the tests, on the main screen.
    - **Test data:** "E2E Chat", joined by User A and User B, with two earlier messages from "Olive Organizer", see [Test data for the game discovery and game chat tests](#test-data-for-the-game-discovery-and-game-chat-tests).
    - **Receive Message only, User B (manual tester):** the "Bea Tester" account described for Search Users, signed in on another device that uses the same back end, and the User B helper running in a second terminal, see [Doing User B's steps](#doing-user-bs-steps). The account must exist before the tests start, so the seed script can add it to "E2E Chat". See also [Running the tests on User A's device only](#running-the-tests-on-user-as-device-only).
    - **User A's message:** each run sends a message with the time in it (`E2E sent by the player <time>`), so one run's message can't be mistaken for an earlier one.

  - **Expected Behaviors, View Messages (Use Case 6, `viewGameChatMessages_showsEarlierMessagesOldestFirst`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. User opens the chat room of a game they joined. | Open the "E2E Chat" chat. |
    | 2. System shows the earlier messages, oldest at the top and newest at the bottom, each with who sent it. | Check that "E2E hello from the organizer" and "E2E bring a ball please" are on the screen.<br>Check that the sender name "Olive Organizer" is shown at least twice.<br>Check that "E2E bring a ball please" is below "E2E hello from the organizer". |

  - **Expected Behaviors, Send Message (Use Case 5, formal Use Case 1, `sendGameChatMessage_sendsTheMessageToTheChat`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1–4. User opens the game's chat room (in place of selecting a friend). | Open the "E2E Chat" chat. |
    | 5a. User clicked "Send" without typing a message. | Input "   " (only spaces) in the text field "Message".<br>Check that the button "Send message" is disabled.<br>Clear the text field "Message". |
    | 5. User types a message and clicks "Send". | Input "E2E sent by the player &lt;time&gt;" in the text field "Message".<br>Check that the button "Send message" is enabled, and click it. |
    | 6. System sends the message to the other members. | Wait until "E2E sent by the player &lt;time&gt;" is on the screen.<br>Check that the button "Send message" is disabled again, since the text field is empty.<br>As the test organizer, read the chat's messages (`GET /api/chats/{chatId}/messages`) until they include "E2E sent by the player &lt;time&gt;". |

  - **Expected Behaviors, Receive Message (Use Case 7, `receiveGameChatMessage_showsNewMessageLive`):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. Another member sends a message while the user has the game's chat open. | Open the "E2E Chat" chat and keep it open.<br>**(manual, User B)** On Home, open "E2E Chat", click "Open Chat" and send any message. Press Enter in the User B helper. |
    | 2. System shows the new message right away, from that member. | Without leaving the chat, wait up to 30 seconds until a message showing the sender name "Bea Tester" is on the screen below "E2E bring a ball please". User B's message can say anything, so only its sender name is checked. |

    A game chat's push notification, when the chat isn't open, works the same way as a friend chat's, which `FriendSystemTest` checks.

  - **Test Logs:**
    ```
    To be added once the tests pass on User A's emulator.
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

- **Use Cases: Accept Friend Request (6), Send Friend Message (9, formal Use Case 1), Receive Friend Message (11), View Friend Messages (10) and Remove Friend (12), test `FriendSystemTest.test3AcceptFriendRequestAndChat`**

  One test covers these five use cases in this order, since the two users can only chat, or stop being friends, once they are friends. Send Friend Message's scenario steps come from its formal specification (Requirements and Design section 3.5, Use Case 1). The other three have none, so their scenario steps describe what the app does.

  - **Before the tests:**
    - **User A (automated):** signed in on the emulator that runs the test, on the main screen.
    - **User B (manual tester):** the "Bea Tester" account described for Search Users, signed in on another device that uses the same back end. User A and User B are not friends and have no pending friend requests.
    - **One device:** see [Running the tests on User A's device only](#running-the-tests-on-user-as-device-only).
    - **User B's steps:** run the User B helper in a second terminal, see [Doing User B's steps](#doing-user-bs-steps). User B can send any message: the test only checks User A's messages by their text, and finds User B's by the sender name "Bea Tester" and where they are in the chat.
    - **User A's messages:** each run sends messages with the time in them (`E2E hello <time>`, `E2E offline <time>`). Removing a friend deletes their chat, so each run starts with an empty chat, and the time keeps the messages apart even if an old chat was left behind.
    - **Notifications:** the test allows the app's notifications with `pm grant ... android.permission.POST_NOTIFICATIONS`, and uses UI Automator to clear old notifications and look for new ones in the notification shade. Push notifications must be set up for User A's emulator (the app's `google-services.json`).
    - **No connection (6a):** the 6a test turns the phone's Wi-Fi and mobile data off with UI Automator (`svc wifi disable`, `svc data disable`), and back on before trying again, waiting for the phone and the chat to reconnect. They are also turned back on after the test if it fails.
    - **After the test:** User A and User B are not friends again, and their chat is deleted. If the test fails before Remove Friend, the tester removes the friendship with Remove friend on the Friends tab.

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
    | 1. The friend sends the user a message while the user doesn't have the chat open. | Click the button "Back".<br>Open the notification shade and click "Clear all" (UI Automator), so an older notification can't count.<br>**(manual, User B)** In the chat with User A, send any message. Press Enter in the User B helper. |
    | 2. System notifies the user with a push notification. | Open the notification shade (UI Automator).<br>Wait until a notification titled "Bea" is shown.<br>Close the notification shade. |

    User B sends only this one message. View Friend Messages then checks it is in the chat, and `GameChatTest` checks a message arriving live in an open chat.

  - **Expected Behaviors, View Friend Messages (Use Case 10):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. User opens the chat with a friend. | Click the "Bea Tester" card on the Friends screen. |
    | 2. System shows the earlier messages, oldest at the top and newest at the bottom, each with who sent it. | Wait until "E2E offline &lt;time&gt;" is on the screen.<br>Check that "E2E hello &lt;time&gt;" is above "E2E offline &lt;time&gt;".<br>Wait until a message showing the sender name "Bea Tester" (User B's message from Receive Friend Message) is on the screen below "E2E offline &lt;time&gt;". |

  - **Expected Behaviors, Remove Friend (Use Case 12):**

    | **Scenario Steps** | **Test Case Steps** |
    | ------------------ | ------------------- |
    | 1. User opens a friend's options and selects "Remove friend". | Click the button "Back" to return to the Friends screen.<br>Click the button "More options for Bea Tester", then "Remove friend". |
    | 2. System asks the user to confirm. | Check that the text "Remove Bea Tester?" is on the screen. |
    | 3. User confirms. | Click the button "Remove". |
    | 4. System ends the friendship and removes the friend from the list. | Wait until the "Bea Tester" card is no longer on the screen.<br>Search for "Bea Tester" and check that the card shows the button "Add". |

  - **Test Logs:**
    ```
    To be added once the test passes on User A's emulator.
    ```
