# Frontend Structure

The Android app lives in `frontend/app/src/main/java/com/example/cpen321application/`. It is written in Kotlin with Jetpack Compose.

The code is grouped by the components in the design doc (User, Game), plus shared code in `core/`, the wiring in `navigation/`, and the screens in `ui/`.

## Folder overview

```
cpen321application/
├── MainActivity.kt            app entry point, sets up the theme and AppNavigation
├── core/                      shared by every component
├── user/                      User component: sign in, sign up, profile
├── game/                      Game component: games, discover, join requests
├── navigation/                connects routes to screens and holds shared state
└── ui/                        screens and reusable UI pieces (drawing only)
```

## core/

| File | What it does |
| --- | --- |
| `network/ServerApi.kt` | Every backend endpoint as a Retrofit function |
| `network/ServerGson.kt` | Gson setup. `@Required` fields make a reply fail to parse if the server leaves them out |
| `network/ReadReply.kt` | Reads a reply's JSON from the body, or from the error body for 4xx/5xx |
| `session/Session.kt` | `Session.call` sends a request with the JWT. If the JWT expired it signs in again silently and retries once. If that fails, the app goes back to sign in |
| `session/AuthTokenStore.kt` | Saves the JWT on the phone, encrypted with AES-256-GCM using a key in the Android Keystore |
| `notifications/PushNotificationService.kt` | Receives Firebase push notifications and shows them |
| `notifications/GameEvents.kt` | Passes notifications to open screens so they reload (e.g. a new join request) |
| `SafeCoroutines.kt` | Coroutine scopes that show a message instead of crashing on an unexpected error |
| `Messages.kt` | Error messages shared by the repositories |

## user/

| File | What it does |
| --- | --- |
| `repository/UserRepository.kt` | Login, sign up, reading the Google profile, and loading/updating the user's profile |
| `dto/UserDtos.kt` | Request and reply classes for the auth and profile endpoints |
| `GoogleSignIn.kt` | Google sign in with Credential Manager, normal and silent |
| `ProfileValidation.kt` | Checks names and birth year before saving a profile |
| `NotificationRegistration.kt` | Sends the phone's Firebase token to the server |

## game/

| File | What it does |
| --- | --- |
| `repository/GameRepository.kt` | Every game call: create, edit, cancel, details, my games, discover, join requests |
| `dto/GameDtos.kt` | Request and reply classes for the game endpoints, matching the server's JSON |
| `model/GameModels.kt` | What the screens show: `GameDetails`, `GameInfoForm`, `GameLocation`, `GameSummary`, and the converters from DTOs |
| `model/DiscoverGame.kt` | A Discover card and its converter |
| `model/JoinRequestItem.kt` | A join request card, the Join Requests header, and their converters |
| `location/DeviceLocation.kt` | Gets the phone's location once with Android's `LocationManager` |
| `location/Geocoding.kt` | Turns a map point into a place name and address |
| `location/PlaceSearch.kt` | Google Places search for the location picker |
| `GameFormat.kt` | Formats times, distances and "spots left" text |
| `GameOptions.kt` | The sports and skill levels the server accepts |

## navigation/

| File | What it does |
| --- | --- |
| `AppNavigation.kt` | Maps each route to its route function, listens for sign-in-required and approval events |
| `Routes.kt` | Route names and `savedStateHandle` keys for passing values between screens |
| `routes/` | One function per screen (see below) |
| `state/` | State that survives leaving a screen (see below) |

**routes/**: each route loads data, keeps short-lived status (loading, errors), calls the repositories, and navigates. It then passes a `UiState` and `Actions` to its screen.

| File | Screens |
| --- | --- |
| `AuthRoutes.kt` | Sign In, Sign Up |
| `AccountRoutes.kt` | Profile, Edit Profile, and the shared `OnResume` helper |
| `HomeRoute.kt` | Home |
| `DiscoverRoute.kt` | Discover, including the location permission |
| `GameDetailsRoute.kt` | Game Details and the join request popup |
| `JoinRequestsRoute.kt` | Join Requests (organiser) |
| `GameFormRoutes.kt` | Create Game, Edit Game, Pick Location |

**state/**: kept for the whole app session.

| File | Holds |
| --- | --- |
| `AppState.kt` | The nav controller, the three states below, sign in/out and tab switching |
| `HomeState.kt` | The user's organised and joined games |
| `DiscoverState.kt` | Discover results, the search location, and the picked place |
| `ProfileState.kt` | The signed-in user's profile |

## ui/

Screens only draw and report taps. They never call the server.

| Folder | Contents |
| --- | --- |
| `components/` | Pieces used by several screens: bottom bar, form fields, chips, map preview, cards |
| `screens/auth/` | Sign In, Sign Up |
| `screens/home/` | Home |
| `screens/discover/` | Discover, plus its search area and game card |
| `screens/games/` | Game Details, Input Game Info (create/edit), Pick Location, Join Requests, and their parts |
| `screens/profile/` | Profile, Edit Profile |
| `theme/` | Colours, fonts and the app theme |

A screen with an `…Parts`, `…Sections`, `…Fields` or `…Card` file next to it was split into smaller files. Those files belong to that screen only.

## How a request flows

Example: the user taps a game card on Discover.

1. **Tap.** `GameCard` (`ui/screens/discover/DiscoverGameCard.kt`) calls its `onClick`. `DiscoverScreen` passes the tap up as `actions.onGameClick(game)`. The screen does nothing else, since screens don't navigate or call the server.
2. **Navigate.** `DiscoverRoute` handles `onGameClick` by calling `navController.navigate(Routes.gameDetails(game.gameId))`, which opens the route `game_details/42`.
3. **Open the route.** `AppNavigation` matches `Routes.GAME_DETAILS` and runs `GameDetailsRoute`. The route reads the id with `backStackEntry.gameId()`. It also checks whether the previous screen was Discover, so the bottom bar highlights Discover and a sent join request returns there.
4. **Load.** When the screen is shown, `OnResume` runs `loadDetails()`. It sets `isLoading = true` and launches a coroutine on `app.scope`.
5. **Repository.** `loadDetails()` calls `fetchGameDetails(context, gameId)` in `game/repository/GameRepository.kt`.
6. **Session.** The repository calls `Session.call`, which reads the JWT (from memory, or from `AuthTokenStore`) and calls `ServerApi.retrofitService.getGameDetails("Bearer <jwt>", gameId)`. This sends `GET /api/games/42`.
   - If the server says the JWT expired, `Session` signs in to Google silently, gets a new JWT from `/api/auth/login`, and sends the request again once.
   - If that fails, `Session` returns `null`, the repository returns `Failed("Your session ended…")`, and `AppNavigation` sends the user back to sign in.
7. **Reply.** Retrofit and Gson turn the JSON into a `GameDetailsResponse` DTO (`game/dto/GameDtos.kt`). The repository returns one of:
   - `Loaded(details, game)` for a successful reply
   - `NotFound` for a 404
   - `Failed(message)` for a network error, server error or unreadable reply
8. **Convert.** On `Loaded`, the route calls `toGameDetails` (`game/model/GameModels.kt`), which formats the time, capitalises the sport, and works out the user's role (organiser, player or viewer).
9. **Show.** The route stores the model in its `details` state and builds a `GameDetailsUiState` for `GameDetailsScreen`. Compose redraws the screen. The role decides what's shown: organisers see Edit and Cancel, players see Leave, viewers see Request to Join.
10. **Stay up to date.** While the screen is open, the route listens to `GameEvents`. When a push notification about this game arrives (a new request, an approval, an edit), it calls `loadDetails()` again. Coming back to the screen also reloads it through `OnResume`.

If loading fails after the game was already shown, the old details stay on screen with the error above them. If the game no longer exists, the screen shows "This game no longer exists." with no Retry button.

## Other flows

**App start**
- `MainActivity` sets up the theme and shows `AppNavigation`.
- `AppState.startDestination()` checks for a saved JWT. With one, the app opens Home and registers for notifications. Without one, it opens Sign In.

**Sign in**
- `SignInRoute` runs Google sign in (`user/GoogleSignIn.kt`), then sends the Google token to `login` in `UserRepository`.
- On success, `AppState.completeSignIn` saves the JWT, opens Home, removes Sign In from the back stack, and registers the phone for notifications.

**Sign up**
- `SignUpRoute` verifies with Google, then prefills the name from the Google token with `readGoogleProfile`.
- Create Account calls `signUp`. If the Google account already has an account, the user goes back to Sign In with a message.

**Push notifications**
1. The backend sends a Firebase message with a `type` (e.g. `join_request`) and a `gameId`.
2. `PushNotificationService` shows the notification and calls `GameEvents.publish`.
3. Any open route for that game reloads. `AppNavigation` also refreshes Home when a join request is approved.

**Session ends**
- Any call whose JWT can't be renewed makes `Session` emit `signInRequired`.
- `AppNavigation` calls `AppState.returnToSignIn`, which clears the saved JWT and all the Home, Discover and Profile state, then shows Sign In with "Your session ended".

## Things to know

- **Result types:** repository functions never throw for normal problems. They return a sealed result like `Loaded` / `Failed(message)`, and the message is ready to show the user.
- **DTO vs model:** DTOs match the server's JSON exactly. Models are what the screens show (formatted times, capitalised names). Converters are in `game/model/`.
- **Live updates:** push notifications go through `GameEvents`. Routes that show a game listen to it and reload when an event for that game arrives.
- **Passing values between screens:** Pick Location sends the chosen place back through `savedStateHandle`, using the keys in `Routes.kt`.
- **Config:** `API_BASE_URL`, `GOOGLE_CLIENT_ID` and `MAPS_API_KEY` come from `frontend/local.properties` through `BuildConfig`. See the README.
- **UiState and Actions:** most screens take a `…UiState` (what to show) and a `…Actions` (what each tap does). Both are defined at the top of the screen's file, so that's the place to start reading a screen.
- **Route state vs app state:** short-lived things like `isCancelling` or a screen's error live in the route with `remember`. Data that should still be there after leaving and coming back, like the Home games or Discover results, lives in `navigation/state/`.
- **Coroutines:** routes launch work on `app.scope`, which comes from `rememberSafeCoroutineScope`. An unexpected error shows "Something went wrong" instead of crashing. Loading flags are always reset in a `finally` block.
- **Location:** the phone's location is read once when Discover needs it, not tracked, to save battery. Sorting by distance happens on the server, and the sport filter runs on the phone.

## Adding a new screen

1. Add the server calls to the component's repository, and their request/reply classes to its `dto/` file.
2. Add a model and converter in `model/` if the screen needs formatted data.
3. Add the screen in `ui/screens/`, with a `UiState` and `Actions` and no server calls.
4. Add a route function in `navigation/routes/` that loads the data and passes it to the screen.
5. Add the route name to `Routes.kt` and register it in `AppNavigation.kt`.

## Not built yet

The design doc's Player Review, Message (chats) and Social (friends) components have no frontend code yet. They should follow the same layout: a folder with `repository/`, `dto/` and `model/`, routes in `navigation/routes/`, and screens in `ui/screens/`.
