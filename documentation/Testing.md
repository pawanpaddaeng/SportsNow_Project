# Testing

## 1. Change History

| **Change Date** | **Modified Sections** | **Rationale** |
| --------------- | --------------------- | ------------- |
| Oct 20, 2026 | 2.1, 4 | First test plan for CP1. Section 2.1 plans the back-end tests for the Game component. Section 4 plans the end-to-end test for Use Case 3, Discover Game. We will write and run the tests, and add the results, coverage, GitHub Actions file and logs, at CP2. |

---

## 2. Back-end Test Specification: APIs

We test every interface of the **Game** component that is built (see Requirements and Design, section 4.1). Two interfaces are only planned and not built yet: `DELETE /api/games/{gameId}/players/{userId}` and `POST /api/games/{gameId}/invites`. We will test them once they are built.

Each interface has two `describe` groups:
- one **without mocks**, for example `describe('Unmocked: GET /api/games/discover', ...)`
- one **with mocks**, for example `describe('Mocked: GET /api/games/discover', ...)`

Each test has comments that say its input, the status code we expect, what should happen, and what it should return. Tests with mocks also say what the mock does.

**Tests without mocks** use a real MySQL test database:
- Before each test, we add the users and games the test needs.
- After each test, we empty every table, so one test can't change another.
- We sign in by making a JWT for the test user with `createAuthToken`. This way the tests don't need Google sign in.

**Tests with mocks** use `jest.spyOn` to:
- make database functions throw an error, to check that the server answers `500 server_error`
- replace the push notification functions (`sendPushToUser` and `sendPushToUsers`), to check who gets a notification and what type it is

### 2.1. Locations of Back-end Tests and Instructions to Run Them

#### 2.1.1. Tests

| **Interface** | **Describe Group Location, No Mocks** | **Describe Group Location, With Mocks** | **Mocked Components** |
| ------------- | ------------------------------------- | --------------------------------------- | --------------------- |
| **POST /api/games** | `backend/tests/no-mock/createGame.test.ts` | `backend/tests/mock/createGame.test.ts` | Games DB, Firebase push |
| **GET /api/games/mine** | `backend/tests/no-mock/getMyGames.test.ts` | `backend/tests/mock/getMyGames.test.ts` | Games DB |
| **GET /api/games/discover** | `backend/tests/no-mock/discoverGames.test.ts` | `backend/tests/mock/discoverGames.test.ts` | Games DB |
| **GET /api/games/{gameId}** | `backend/tests/no-mock/getGameDetails.test.ts` | `backend/tests/mock/getGameDetails.test.ts` | Games DB, Users DB |
| **PUT /api/games/{gameId}** | `backend/tests/no-mock/updateGame.test.ts` | `backend/tests/mock/updateGame.test.ts` | Games DB, Firebase push |
| **POST /api/games/{gameId}/cancel** | `backend/tests/no-mock/cancelGame.test.ts` | `backend/tests/mock/cancelGame.test.ts` | Games DB, Firebase push |
| **POST /api/games/{gameId}/join-requests** | `backend/tests/no-mock/sendJoinRequest.test.ts` | `backend/tests/mock/sendJoinRequest.test.ts` | Join Requests DB, Firebase push |
| **GET /api/games/{gameId}/join-requests** | `backend/tests/no-mock/listJoinRequests.test.ts` | `backend/tests/mock/listJoinRequests.test.ts` | Games DB, Join Requests DB |
| **PATCH /api/games/{gameId}/join-requests/{requestId}** | `backend/tests/no-mock/answerJoinRequest.test.ts` | `backend/tests/mock/answerJoinRequest.test.ts` | Join Requests DB, Firebase push |

We will add links to the line where each `describe` group starts at CP2, once the test files exist.

#### Planned test cases

**Tests for every interface**

Every route checks that the user is signed in first. So each interface's group without mocks also has these tests:

| **Test** | **Input** | **Expected status** | **What should happen** |
| -------- | --------- | ------------------- | ---------------------- |
| No token | No `Authorization` header | 400 | Nothing changes. Returns `{ msg: "invalid_request" }` |
| Bad token | `Authorization: Bearer not-a-jwt` | 401 | Nothing changes. Returns `{ msg: "invalid_token" }` |
| Expired token | A JWT that has expired | 401 | Nothing changes. Returns `{ msg: "expired_token" }` |
| User doesn't exist | A JWT for a user id with no account | 401 | Nothing changes. Returns `{ msg: "no_user" }` |

Each interface's group with mocks also has a test where the database function throws an error. We expect `500` with `{ msg: "server_error" }`, and nothing changes.

**POST /api/games** (create a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Valid game | All fields, starting tomorrow | 201 | The game is saved. The user is its organizer and first player. Returns the game with `player_count` 1 and `status` "scheduled" |
| No mocks | No title | Body without `title` | 400 | No game is saved. The error says `title` is required |
| No mocks | Unknown sport | `sport: "chess"` | 400 | No game is saved. The error lists the allowed sports |
| No mocks | Start in the past | `starts_at` is yesterday | 400 | No game is saved. The error says `starts_at must be in the future` |
| No mocks | End before start | `ends_at` is before `starts_at` | 400 | No game is saved. The error says `ends_at must be after starts_at` |
| No mocks | Too many players | `max_players: 101` | 400 | No game is saved. The error says it must be 2 to 100 |
| No mocks | Bad location | `latitude: 120` | 400 | No game is saved. The error gives the allowed ranges |
| With mocks | Database fails | `createGame` throws | 500 | Returns `{ msg: "server_error" }` |
| With mocks | Test notification | Valid game, `sendPushToUser` replaced, fake timers | 201 | After 5 seconds, the organizer gets a `game_created` notification |

**GET /api/games/mine** (the user's games)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | No games | A user with no games | 200 | Returns `{ msg: "no_games" }` |
| No mocks | Organized and joined | The user organizes one game and plays in another | 200 | `organized_games` and `joined_games` each have the right game, soonest first |
| With mocks | Database fails | `getGamesForUser` throws | 500 | Returns `{ msg: "server_error" }` |

**GET /api/games/discover** (games near the user)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Nearby games, closest first | Three games by other users, 200 m, 1 km and 2 km away | 200 | Returns the three games in that order. Each has `distance_m`, and `join_request_status` is null |
| No mocks | Games that should not show | The user's own game, a game they joined, a cancelled game, a past game, and a game 60 km away | 200 | Returns `{ msg: "no_games" }` |
| No mocks | Full game still shows | A nearby game with no spots left | 200 | The game is in the list, with `player_count` equal to `max_players` |
| No mocks | Request status | The user has a pending request for a nearby game | 200 | That game has `join_request_status: "pending"` |
| No mocks | No location | No `lat` in the query | 400 | Returns `{ msg: "invalid_request" }` |
| No mocks | Location out of range | `lat=95&lng=0` | 400 | Returns `{ msg: "invalid_request" }` |
| With mocks | Database fails | `getDiscoverGames` throws | 500 | Returns `{ msg: "server_error" }` |

**GET /api/games/{gameId}** (game details)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Organizer | The organizer opens their game, which has two pending requests | 200 | `viewer_role` is "organizer" and `pending_requests` is 2 |
| No mocks | Player | A user opens a game they joined | 200 | `viewer_role` is "player" |
| No mocks | Viewer | A user opens a game they asked to join | 200 | `viewer_role` is "viewer" and `join_request_status` is "pending" |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| No mocks | Bad id | `/api/games/abc` | 400 | Returns `{ msg: "invalid_request" }` |
| With mocks | Database fails | `getGameById` throws | 500 | Returns `{ msg: "server_error" }` |
| With mocks | Organizer not in players list | `getGamePlayers` doesn't return the organizer, `findUserById` does | 200 | `organizer` comes from the user lookup |
| With mocks | Organizer not found | Neither lookup finds the organizer | 500 | Returns `{ msg: "server_error" }` |

**PUT /api/games/{gameId}** (edit a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Valid edit | The organizer changes the title and time | 200 | The game is updated. Returns the game with the new values |
| No mocks | Not the organizer | Another user edits the game | 403 | The game doesn't change. Returns `{ msg: "forbidden" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| No mocks | Cancelled game | Edit a cancelled game | 409 | The game doesn't change. Returns `{ msg: "game_cancelled" }` |
| No mocks | Game already started | Edit a game that already started | 409 | The game doesn't change. Returns `{ msg: "game_started" }` |
| No mocks | Too few spots | `max_players` is less than the players already in the game | 400 | The game doesn't change. The error says how many players are in it |
| No mocks | Bad field | `sport: "chess"` | 400 | The game doesn't change. Returns `{ msg: "invalid_request" }` |
| With mocks | Players are told | Valid edit, `sendPushToUsers` replaced | 200 | Players and people with pending requests (not the organizer) get a `game_updated` notification that says what changed |
| With mocks | Nothing changed | Same values as before, `sendPushToUsers` replaced | 200 | No notification is sent |
| With mocks | Database fails | `updateGame` throws | 500 | Returns `{ msg: "server_error" }` |

**POST /api/games/{gameId}/cancel** (cancel a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Cancel | The organizer cancels an upcoming game | 200 | The game's `status` becomes "cancelled". Returns the game |
| No mocks | Not the organizer | Another user cancels it | 403 | The game doesn't change. Returns `{ msg: "forbidden" }` |
| No mocks | Already cancelled | Cancel the same game twice | 409 | Returns `{ msg: "game_cancelled" }` |
| No mocks | Game already started | Cancel a game that already started | 409 | The game doesn't change. Returns `{ msg: "game_started" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| With mocks | Players are told | `sendPushToUsers` replaced | 200 | Players and people with pending requests get a `game_cancelled` notification |
| With mocks | Database fails | `cancelGame` throws | 500 | Returns `{ msg: "server_error" }` |

**POST /api/games/{gameId}/join-requests** (ask to join a game)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Valid request | `skill_level`, `play_frequency` and `message` for another user's game | 201 | A pending request is saved with the answers |
| No mocks | Only skill level | `{ skill_level: "beginner" }` | 201 | Saved, with `play_frequency` and `message` empty |
| No mocks | Ask again | The user's earlier request was declined | 201 | The same request goes back to pending, with the new answers |
| No mocks | Own game | The organizer asks to join their own game | 409 | Nothing is saved. Returns `{ msg: "own_game" }` |
| No mocks | Already a player | The user is already in the game | 409 | Returns `{ msg: "already_player" }` |
| No mocks | Already asked | A second request while the first is still pending | 409 | Returns `{ msg: "already_requested" }` |
| No mocks | Game is full | No spots left | 409 | Returns `{ msg: "game_full" }` |
| No mocks | Cancelled or started | A cancelled game, and a game that already started | 409 | Returns `{ msg: "game_cancelled" }` and `{ msg: "game_started" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| No mocks | Bad answers | No `skill_level`; an unknown `play_frequency`; a message of 201 characters | 400 | Nothing is saved. The error names the bad field |
| With mocks | Organizer is told | Valid request, `sendPushToUser` replaced | 201 | The organizer gets a `join_request` notification with the game's id |
| With mocks | Database fails | `createJoinRequest` throws | 500 | Returns `{ msg: "server_error" }` |

**GET /api/games/{gameId}/join-requests** (the organizer's list of requests)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Pending requests | The organizer's game has two pending requests and one declined | 200 | Returns only the two pending requests, newest first, with each person's name and answers |
| No mocks | No requests | Nobody has asked to join | 200 | `requests` is an empty list |
| No mocks | Not the organizer | Another user asks for the list | 403 | Returns `{ msg: "forbidden" }` |
| No mocks | Game doesn't exist | An id with no game | 404 | Returns `{ msg: "not_found" }` |
| With mocks | Database fails | `getPendingJoinRequests` throws | 500 | Returns `{ msg: "server_error" }` |

**PATCH /api/games/{gameId}/join-requests/{requestId}** (approve or decline a request)

| **Group** | **Test** | **Input** | **Expected status** | **What should happen** |
| --------- | -------- | --------- | ------------------- | ---------------------- |
| No mocks | Approve | `{ decision: "approve" }` for a pending request | 200 | The request is approved and the person joins the game. `player_count` goes up by one |
| No mocks | Decline | `{ decision: "decline" }` | 200 | The request is declined. The players don't change |
| No mocks | Game is full | Approve when no spots are left | 409 | Nothing changes. Returns `{ msg: "game_full" }` |
| No mocks | Already answered | Answer the same request twice | 409 | Returns `{ msg: "already_responded" }` |
| No mocks | Request doesn't exist | A request id that isn't in this game | 404 | Returns `{ msg: "request_not_found" }` |
| No mocks | Not the organizer | Another user answers | 403 | Nothing changes. Returns `{ msg: "forbidden" }` |
| No mocks | Cancelled game | Answer a request for a cancelled game | 409 | Returns `{ msg: "game_cancelled" }` |
| No mocks | Bad input | `{ decision: "maybe" }`, and a bad request id | 400 | Returns `{ msg: "invalid_request" }` |
| With mocks | Person is told | Approve one request and decline another, `sendPushToUser` replaced | 200 | The first person gets `join_request_approved` and the second gets `join_request_declined` |
| With mocks | Database fails | `decideJoinRequest` throws | 500 | Returns `{ msg: "server_error" }` |

#### 2.1.2. Commit Hash Where Tests Run

_To be added at CP2, once the tests are written._

#### 2.1.3. Explanation on How to Run the Tests

**Note:** These steps can only be used once the tests are written at CP2. Right now, only the test plan above exists.

1. **Clone the repository:**
   ```
   git clone https://github.com/pawanpaddaeng/SportsNow_Project.git
   cd SportsNow_Project
   ```
2. **Install the back-end packages:**
   ```
   cd backend
   npm install
   cd ..
   ```
3. **Start the test database.** _We will add these steps at CP2, with the test database setup._
4. **Run the tests.** _This step only works once the tests are written at CP2._ The script runs the tests with mocks, then without mocks, then both together. Each run saves a coverage report in `backend/coverage/`.
   - macOS / Linux: `./scripts/run-backend-interface-tests.sh`
   - Windows: `.\scripts\run-backend-interface-tests.ps1`

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

`frontend/app/src/androidTest/java/com/example/cpen321application/e2e/DiscoverGameTest.kt` _(written at CP2)_

### 4.2. Tests

- **Use Case: Discover Game (Use Case 3)**

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
