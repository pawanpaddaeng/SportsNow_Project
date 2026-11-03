# Backend Structure

The backend lives in `backend/src/`. It is a Node.js server written in TypeScript with Express, and it stores data in MySQL. It runs with Docker Compose (see the README).

The code is grouped by the components in the design doc (User, Game, Message), plus shared code in `core/`. This matches the frontend layout.

## Folder overview

```
backend/src/
├── index.ts          starts the server on PORT (3000 by default) and the chat WebSocket on WS_PORT (8080)
├── app.ts            creates the Express app and mounts every router
├── core/             shared by every component
├── user/             User component: sign up, login, JWTs, profile
├── game/             Game component: games, discover, join requests
└── chat/             Message component: game chats, message history, WebSocket
```

**Dependency rules**
- `core/` imports nothing from `user/`, `game/` or `chat/`.
- `game/` may import from `user/` (for `requireAuth` and `getAuthUser`). `user/` never imports from `game/`.
- `chat/` may import from `user/` (to check the JWT). `game/` imports `chat/db/chats.ts` to open a new game's chat and read its id. `chat/` never imports from `game/`, it reads the game tables with SQL.
- `app.ts` is the only place routers are mounted.

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
| `auth/jwt.ts` | Creates and checks the app's JWTs (HS256, signed with `JWT_SECRET`, valid for 30 days) |
| `auth/middleware.ts` | `requireAuth` checks the JWT on every protected route and loads the user. `getAuthUser(res)` gives the route that user. `checkToken` is the check itself, also used by the chat WebSocket |
| `db/users.ts` | User queries: create, find by id or Google id, update profile, and `userJson` |
| `validation/profile.ts` | Checks first name, last name and birth year |

## game/

| File | What it does |
| --- | --- |
| `routes/gameRoutes.ts` | Create, my games, discover, game details, edit and cancel (endpoints below) |
| `routes/joinRequestRoutes.ts` | Send a join request, list pending requests, approve or decline |
| `routes/gameJson.ts` | The JSON shapes sent to the app for games, players, Discover results and join requests |
| `routes/gameErrors.ts` | Game id parsing, `readBackGame`, and the error tables that map problems like `game_full` to a status and message |
| `db/games.ts` | Game queries: create, read, my games, discover (distance search), update, cancel, players, and `lockGameForChange` |
| `db/joinRequests.ts` | Join request queries: create, list pending, approve/decline, pending count and the user's request status |
| `validation/newGame.ts` | Checks a create/edit game body: sport, title, times, location, max players and skill level |
| `validation/joinRequest.ts` | Checks a join request body: skill level, how often they play and the message |
| `validation/coordinates.ts` | Checks the `lat` and `lng` query values for Discover |

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
- **Row locking:** edits, cancels, join requests and approvals lock the game's row with `FOR UPDATE` inside `inTransaction`, so the player limit can't be exceeded by two approvals at once.
- **Times:** stored as UTC in MySQL and sent to the app as ISO 8601 strings, e.g. `"2026-10-18T02:00:00Z"`.
- **Notifications are best effort:** sending never blocks or fails a reply. The database is the source of truth, and the app reloads when it next opens a screen.
- **Database tables:** created from `mysql/init/*.sql` (users, device tokens, games, game players, join requests, chats) the first time the MySQL container starts. They don't run again on an existing database: a change to a table needs its own script or `ALTER TABLE` run by hand (see the README).
- **Route order:** `/api/games/mine` and `/api/games/discover` are registered before `/api/games/:gameId`, so `mine` and `discover` aren't read as game ids.

## Adding a new endpoint

1. Add the query to the component's `db/` file, returning a result type for rule problems.
2. Add a body parser in `validation/` if the endpoint takes input, returning a `ParseResult`.
3. Add the route in the component's `routes/` file: `requireAuth`, wrap the handler in `asyncRoute`, check input with `invalidRequest`, and map problems through an error table.
4. If it's a new router, mount it in `app.ts`.
5. Add the endpoint to the design doc's interfaces.

## Not built yet

The design doc's Player Review and Social (friends) components have no backend code yet, and friend chat isn't part of `chat/` yet. They should follow the same layout: a folder with `routes/`, `db/` and `validation/`, using `core/` and `requireAuth` from `user/`.
