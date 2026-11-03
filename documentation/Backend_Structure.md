# Backend Structure

The backend lives in `backend/src/`. It is a Node.js server written in TypeScript with Express, and it stores data in MySQL. It runs with Docker Compose (see the README).

The code is grouped by the components in the design doc (User, Game, Social, Message), plus shared code in `core/`. This matches the frontend layout.

## Folder overview

```
backend/src/
├── index.ts          starts the server on PORT (3000 by default) and the chat WebSocket on WS_PORT (8080)
├── app.ts            creates the Express app and mounts every router
├── core/             shared by every component
├── user/             User component: sign up, login, JWTs, profile
├── game/             Game component: games, discover, join requests
├── friend/           Social component: user search, friend requests, friends list
├── chat/             Message component: game chats, message history, WebSocket
└── review/           Player Review component: ratings and reputation scores
```

**Dependency rules**
- `core/` imports nothing from `user/`, `game/`, `friend/` or `chat/`.
- `game/` may import from `user/` (for `requireAuth` and `getAuthUser`). `user/` never imports from `game/`.
- `friend/` may import from `user/` (for `requireAuth` and `getAuthUser`) and from `game/routes/gameErrors.ts` (for id parsing and the error helpers). Nothing imports from `friend/` except `app.ts`, and `game/db/invites.ts` for `areFriends`.
- `user/routes/publicProfileRoutes.ts` imports `getReputation` from `review/` and `getFriendshipStatus` from `friend/db/friends.ts` to build a player's public profile. `review/` imports only from `core/` and reads the game tables with SQL.
- `chat/` may import from `user/` (to check the JWT). `game/` imports `chat/db/chats.ts` to open a new game's chat and read its id. `chat/` never imports from `game/`, it reads the game tables with SQL.

## core/

| File | What it does |
| --- | --- |
| `config/env.ts` | Reads the environment variables (`PORT`, `WS_PORT`, `JWT_SECRET`, MySQL credentials) and fails at start if a required one is missing |
| `db/mysql.ts` | The MySQL connection pool, `selectRows` (SELECT), `executeWrite` (INSERT/UPDATE/DELETE) and `inTransaction` |
| `http/asyncRoute.ts` | Wraps async route handlers. Anything they throw becomes a `500 server_error` reply with a friendly message, and the real error is logged |
| `http/responses.ts` | `invalidRequest(res, error)`, the shared `400 invalid_request` reply |
| `notifications/push.ts` | Sends Firebase push notifications to every device a user is signed into, and deletes tokens Firebase reports as dead |
| `notifications/deviceTokens.ts` | Saves, reads and deletes the phones' Firebase tokens |
| `validation.ts` | `ParseResult`, the result type every body parser returns, and `readId` for ids in a request |
| `logger.ts` | `logInfo` and `logError`. Messages are kept on one line so a value can't fake a log line |

## user/

| File | What it does |
| --- | --- |
| `routes/authRoutes.ts` | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/register_notif` |
| `routes/userRoutes.ts` | `GET /api/users/me`, `PUT /api/users/me` |
| `routes/publicProfileRoutes.ts` | `GET /api/users/:userId`, another player's public profile: name, age range (never the birth year or email), games played, reputation and friendship status. Mounted after the routers for `/api/users/me` and `/api/users/search` |
| `auth/jwt.ts` | Creates and checks the app's JWTs (HS256, signed with `JWT_SECRET`, valid for 30 days) |
| `auth/middleware.ts` | `requireAuth` checks the JWT on every protected route and loads the user. `getAuthUser(res)` gives the route that user. `checkToken` is the check itself, also used by the chat WebSocket |
| `db/users.ts` | User queries: create, find by id or Google id, update profile, and `userJson` |
| `validation/profile.ts` | Checks first name, last name and birth year |

## game/

| File | What it does |
| --- | --- |
| `routes/gameRoutes.ts` | Create, my games, discover, game details, edit and cancel (endpoints below) |
| `routes/joinRequestRoutes.ts` | Send a join request, list pending requests, approve or decline |
| `routes/inviteRoutes.ts` | `POST /api/games/:gameId/invites`: the organiser invites friends, each gets a `game_invite` notification |
| `routes/gameJson.ts` | The JSON shapes sent to the app for games, players, Discover results and join requests |
| `routes/gameErrors.ts` | Game id parsing, `readBackGame`, and the error tables that map problems like `game_full` to a status and message |
| `db/games.ts` | Game queries: create, read, my games, discover (distance search), update, cancel, players, and `lockGameForChange` |
| `db/joinRequests.ts` | Join request queries: create, list pending, approve/decline, pending count and the user's request status |
| `db/invites.ts` | `checkInvites`: the game is the organiser's and not cancelled, started or full, and everyone invited is their friend and not in it yet. Invites aren't stored |
| `validation/newGame.ts` | Checks a create/edit game body: sport, title, times, location, max players and skill level |
| `validation/joinRequest.ts` | Checks a join request body: skill level, how often they play and the message |
| `validation/invite.ts` | Checks an invite body: `friend_ids` is a list of 1 to 100 user ids |
| `validation/coordinates.ts` | Checks the `lat` and `lng` query values for Discover |

## friend/

| File | What it does |
| --- | --- |
| `routes/friendRoutes.ts` | `GET /api/users/search`, `GET /api/friends`, `GET` and `POST /api/friends/requests`, `PATCH /api/friends/requests/:requestId` |
| `db/friends.ts` | Friend queries: search, friends list, `areFriends`, `getFriendshipStatus`, incoming requests, send a request, accept or decline |
| `search/nameMatch.ts` | Decides how well a name matches what was typed: same name, starts with it, contains it, or only similar (counting letter changes, so typos still match) |

## review/

| File | What it does |
| --- | --- |
| `routes/ratingRoutes.ts` | The players a user can rate, submitting a rating, and the user's own score (`GET /api/users/me/stats`) |
| `db/ratings.ts` | Rating queries, and `getReputationScore`, which `game/` uses to show organizers a requester's score |
| `db/reputation.ts` | `getReputation`: a player's games played (completed games they organized, are in, or were approved for) and reputation score, for the public profile. Its badges are empty for now |
| `validation/rating.ts` | Checks a rating body: who is rated and the stars in every category |
| `reputation.ts` | The score rules, with no database so they can be tested on their own |

A completed game's players and no-shows are copied to `game_attendance`, because leaving a completed game deletes its `game_players` row and the last player leaving deletes the game. A player's score mustn't change when that happens. Players are sent their own overall score, but never what they were rated in a game.

## chat/

| File | What it does |
| --- | --- |
| `routes/chatRoutes.ts` | `GET /api/chats/:chatId/messages`, a page of earlier messages for a current player |
| `routes/chatJson.ts` | The JSON shape of a message, the same from the endpoint and the WebSocket |
| `socket/chatSocket.ts` | The WebSocket server: checks who connects, sends messages to everyone in the chat, and drops dead connections |
| `db/chats.ts` | Chat queries: create a game's chat, read access, read a page of messages, save a message |
| `validation/sendMessage.ts` | Checks a `send_message` request: its type, chat id and text |

A chat has no stored open or closed state. It's closed when its game's status is `completed` or `cancelled`, and its readers are the game's current players, so both are always up to date. Deleting a game deletes its chat and messages (`ON DELETE CASCADE`).

The endpoints are listed in the Requirements and Design doc (section 4.1 Main Components). Every endpoint except sign up, login and `/health` needs `Authorization: Bearer <jwt>`.

## How a request flows

Example: a player taps **Send Request** in the app's join request popup.

1. **Request arrives.** The app sends `POST /api/games/42/join-requests` with `Authorization: Bearer <jwt>` and a body like `{ "skill_level": "intermediate", "play_frequency": "weekly", "message": "..." }`.
2. **Router.** `app.ts` parses the JSON body with `express.json()` and passes the request to `joinRequestsRouter`, which matches the path.
3. **Auth.** `requireAuth` (`user/auth/middleware.ts`) reads the JWT and checks it with `verifyAuthToken`. It then loads the user with `findUserById`. If the token is missing, expired or invalid, it replies `400` or `401` and the handler never runs. Otherwise it stores the user for `getAuthUser(res)`.
4. **Check the input.** The handler reads the game id with `requireGameId` (`gameErrors.ts`), then checks the body with `parseJoinRequest` (`validation/joinRequest.ts`). A bad value replies `400 invalid_request` through `invalidRequest`.
5. **Database.** `createJoinRequest` (`db/joinRequests.ts`) runs in `inTransaction`:
   - It reads the game with `SELECT … FOR UPDATE`, which locks the game's row until the transaction ends. Two requests or approvals at the same moment can't both pass the checks.
   - It checks the rules: the game exists, isn't the user's own, isn't cancelled, started or full, and the user isn't already a player.
   - It checks the user doesn't already have a pending request. A previously declined request is reset to pending instead of creating a new row.
   - It returns `{ ok: true, requestId, organizerId, title }`, or `{ ok: false, problem }` such as `game_full`.
6. **Problems become replies.** If `ok` is false, the route looks the problem up in `JOIN_REQUEST_ERRORS` with `responseFor` and replies, e.g. `409 { msg: "game_full", error: "This game is full" }`.
7. **Notify.** On success, the route calls `sendPushToUser` (`core/notifications/push.ts`) for the organiser, with `data: { type: "join_request", gameId: "42" }`. It doesn't wait for the notification. A failure is only logged.
8. **Reply.** The route replies `201 { msg: "success", request: { request_id, game_id, status: "pending" } }`.
9. **On the organiser's phone,** the notification arrives with `type: "join_request"`, and an open Join Requests screen for game 42 reloads.

If anything throws along the way (e.g. the database is down), `asyncRoute` catches it, `handleRouteErrors` logs it with `logError`, and the app gets `500 { msg: "server_error", error: "Could not send your request, try again later" }`.

## Things to know

- **Reply format:** every reply has a `msg` code the app checks, e.g. `success`, `no_games`, `invalid_request`, `game_full`, `server_error`. Errors also have a readable `error` message the app can show.
- **Result types instead of exceptions:** `db` functions return `{ ok: true, ... }` or `{ ok: false, problem }` for rule problems the user can act on. Routes turn each problem into a reply using an error table. Exceptions are only for unexpected failures.
- **Error tables:** `GAME_CHANGE_ERRORS` (in `gameErrors.ts`), `JOIN_REQUEST_ERRORS` and `DECISION_ERRORS` (in `joinRequestRoutes.ts`) are typed `Record`s keyed by every possible problem. If a new problem is added, the compiler forces it to have a reply.
- **Row locking:** edits, cancels, join requests and approvals lock the game's row with `FOR UPDATE` inside `inTransaction`, so the player limit can't be exceeded by two approvals at once. Friend requests lock both users' rows the same way, lowest id first, so two people asking each other at once can't both succeed.
- **Friendships:** one friendship is two rows in `friendships`, one for each user, so a user's friends are found by `user_id` alone.
- **Name search:** MySQL can't rank names by how similar they are, so `searchUsers` reads every user's name and compares them on the server. That is fine at this size, but a much larger user base would need a search index.
- **Times:** stored as UTC in MySQL and sent to the app as ISO 8601 strings, e.g. `"2026-10-18T02:00:00Z"`.
- **Notifications are best effort:** sending never blocks or fails a reply. The database is the source of truth, and the app reloads when it next opens a screen.
- **Database tables:** created from `mysql/init/*.sql` (users, device tokens, games, game players, join requests, chats, friend requests, friendships, game attendance, player ratings) the first time the MySQL container starts. They don't run again on an existing database: a change to a table needs its own script or `ALTER TABLE` run by hand (see the README).
- **Route order:** `/api/games/mine` and `/api/games/discover` are registered before `/api/games/:gameId`, so `mine` and `discover` aren't read as game ids.

## Adding a new endpoint

1. Add the query to the component's `db/` file, returning a result type for rule problems.
2. Add a body parser in `validation/` if the endpoint takes input, returning a `ParseResult`.
3. Add the route in the component's `routes/` file: `requireAuth`, wrap the handler in `asyncRoute`, check input with `invalidRequest`, and map problems through an error table.
4. If it's a new router, mount it in `app.ts`.
5. Add the endpoint to the design doc's interfaces.

## Not built yet

Player Review's stats endpoint and badges aren't built yet, Social doesn't have removing friends or friend profiles yet, and friend chat isn't part of `chat/` yet. New components should follow the same layout: a folder with `routes/`, `db/` and `validation/`, using `core/` and `requireAuth` from `user/`.
