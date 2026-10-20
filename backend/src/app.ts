import express, { type Express, type Request, type Response } from 'express';
import { OAuth2Client } from 'google-auth-library';
import WebSocket from 'ws'
import { createAuthToken } from './auth/jwt';
import { getAuthUser, requireAuth } from './auth/middleware';
import { MAX_FCM_TOKEN_LENGTH, saveDeviceToken } from './db/deviceTokens';
import {
  cancelGame, 
  countPendingJoinRequests,
  createGame,
  discoverGameJson,
  type Game,
  type GameChangeProblem,
  gameJson,
  gamePlayerJson,
  getDiscoverGames,
  getGameById,
  getGamePlayers,
  getGamesForUser,
  getJoinRequestStatus,
  getUsersToNotify,
  updateGame,
} from './db/games';
import {
  createJoinRequest,
  decideJoinRequest,
  getPendingJoinRequests,
  type JoinRequestDecisionProblem,
  type JoinRequestProblem,
  pendingJoinRequestJson,
} from './db/joinRequests';
import { parseCoordinates } from './validation/coordinates';
import { parseJoinRequest, type JoinRequestBody } from './validation/joinRequest';
import { parseNewGame, type CreateGameBody } from './validation/newGame';
import { sendPushToUser, sendPushToUsers } from './notifications/push';
import { asyncRoute } from './http/asyncRoute';
import { logInfo } from './logger';
import { createUser, findUserByGoogleId, findUserById, isDuplicateEntryError,updateUserProfile, userExists, userJson } from './db/users';
import { parseProfileFields, type ProfileBody } from './validation/profile';

// how long after creating a game the debug notification is sent
const DEBUG_NOTIFICATION_DELAY_MS = 5000;

// notifications are best effort, a failure is only logged
function logNotificationError(error: unknown): void {
  console.error('Sending a notification failed:', error);
}

// largest games.game_id (INT UNSIGNED)
const MAX_GAME_ID = 4294967295;

// the game id from a route like /api/games/:gameId, or null if it isn't a valid id
function readGameId(raw: string): number | null {
  const gameId = /^\d{1,10}$/.test(raw) ? Number(raw) : NaN;
  return Number.isSafeInteger(gameId) && gameId >= 1 && gameId <= MAX_GAME_ID ? gameId : null;
}

// response for a game the organizer can't change, shared by edit and cancel
function gameChangeError(problem: GameChangeProblem): { status: number; body: { msg: string; error: string } } {
  switch (problem) {
    case 'not_found':
      return { status: 404, body: { msg: 'not_found', error: 'This game does not exist' } };
    case 'not_organizer':
      return { status: 403, body: { msg: 'forbidden', error: 'Only the organizer can change this game' } };
    case 'cancelled':
      return { status: 409, body: { msg: 'game_cancelled', error: 'This game was cancelled' } };
    case 'started':
      return { status: 409, body: { msg: 'game_started', error: 'This game has already started' } };
  }
}

// response for a join request that can't be made
function joinRequestError(problem: JoinRequestProblem): { status: number; body: { msg: string; error: string } } {
  switch (problem) {
    case 'not_found':
      return { status: 404, body: { msg: 'not_found', error: 'This game does not exist' } };
    case 'own_game':
      return { status: 409, body: { msg: 'own_game', error: 'You organize this game' } };
    case 'cancelled':
      return { status: 409, body: { msg: 'game_cancelled', error: 'This game was cancelled' } };
    case 'started':
      return { status: 409, body: { msg: 'game_started', error: 'This game has already started' } };
    case 'already_player':
      return { status: 409, body: { msg: 'already_player', error: "You're already in this game" } };
    case 'game_full':
      return { status: 409, body: { msg: 'game_full', error: 'This game is full' } };
    case 'already_requested':
      return {
        status: 409,
        body: { msg: 'already_requested', error: 'You already asked to join this game, wait for the organizer to respond' },
      };
  }
}

// response for an approval or decline the organizer can't make
function decisionError(problem: JoinRequestDecisionProblem): { status: number; body: { msg: string; error: string } } {
  switch (problem) {
    case 'request_not_found':
      return { status: 404, body: { msg: 'request_not_found', error: 'This join request does not exist' } };
    case 'already_responded':
      return {
        status: 409,
        body: { msg: 'already_responded', error: 'This request was already answered or withdrawn' },
      };
    case 'game_full':
      return { status: 409, body: { msg: 'game_full', error: 'This game is full, add spots before approving more players' } };
    default:
      return gameChangeError(problem);
  }
}

// what an edit changed, in words for the notification, e.g. ["time", "location"]
function describeChanges(before: Game, after: Game): string[] {
  const changes: string[] = [];
  if (before.title !== after.title) changes.push('title');
  if (before.sport !== after.sport) changes.push('sport');
  if (before.startsAt !== after.startsAt || before.endsAt !== after.endsAt) changes.push('time');
  if (before.locationName !== after.locationName || before.address !== after.address ||
      before.latitude !== after.latitude || before.longitude !== after.longitude) {
    changes.push('location');
  }
  if (before.maxPlayers !== after.maxPlayers) changes.push('max players');
  if (before.skillLevel !== after.skillLevel) changes.push('skill level');
  if (before.description !== after.description) changes.push('description');
  return changes;
}



export function createApp(): Express {
  const app = express();
  app.use(express.json())
  const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

  //const courseWs = new WebSocket('wss://8.229.22.124');

  //start websocket to transfer pixels to client
  const server = new WebSocket.Server({ port: 8080 });

  let clients = new Set();

  //add clients to set when connected
  server.on('connection', (ws) => {
    logInfo('CLIENT CONNECTED TO WEBSOCKET')
    clients.add(ws);
    ws.on('close', () => clients.delete(ws));
  });

  // forward every incoming pixel to all connected clients
  // courseWs.on('message', (data) => {
  //   const payload = data.toString();
  //   for (let client of clients) {
  //     if (client.readyState === WebSocket.OPEN) {
  //       console.log("Sending Pixel")
  //       client.send(payload);
  //     }
  //   }
  // });

  interface GoogleAuthRequestBody {
    idToken: string;
  }

  app.post('/api/auth/google', asyncRoute(async (req: Request<Record<string, never>, unknown, GoogleAuthRequestBody | undefined>, res: Response) => {
    const { idToken } = req.body ?? {};
    logInfo('Received an authorization request');
    

    if (!idToken) {
      return res.status(400).json({ error: 'idToken is required' });
    }

    try {
      // Verify the token with Google
      const ticket = await googleClient.verifyIdToken({
        idToken,
        audience: process.env.GOOGLE_CLIENT_ID,
      });

      const payload = ticket.getPayload();

      if (!payload) {
        return res.status(401).json({ error: 'Invalid token payload' });
      }

      const firstName = payload.given_name || '';
      const lastName = payload.family_name || '';
      logInfo('Successfully authenticated user');

      //send user info back, add more to this when adding session ids
      return res.status(200).json({
        message: 'Authentication successful',
        user: {
          firstName,
          lastName,
        },
      });
    } catch (error) {
      console.error('Token verification failed:', error);
      return res.status(401).json({ error: 'Invalid Google ID token' });
    }
  }));

  interface SignUpRequestBody {
    id_token?: unknown;
    email?: unknown;
    f_name?: unknown;
    l_name?: unknown;
    birthYear?: unknown;
  }

  /*  sign up: verifies the Google token, adds the user to the database and
      returns a JWT for future requests. Responds already_exists if the
      Google account already has a user, they should sign in instead
   */
  app.post('/api/auth/signup', asyncRoute(async (req: Request<Record<string, never>, unknown, SignUpRequestBody | undefined>, res: Response) => {
    const { id_token, email } = req.body ?? {};
    logInfo('Received a sign up request');

    if (typeof id_token !== 'string' || id_token === '' || typeof email !== 'string' || email === '') {
      return res.status(400).json({ msg: 'invalid_request', error: 'id_token and email are required' });
    }

    const profile = parseProfileFields(req.body);
    if(!profile.ok) {
      return res.status(400).json({ msg: 'invalid_request', error: profile.error });
    }

    const{ firstName, lastName, birthYear: year } = profile.value;
    let googleId: string;
    let verifiedEmail: string;
    try {
      const ticket = await googleClient.verifyIdToken({
        idToken: id_token,
        audience: process.env.GOOGLE_CLIENT_ID,
      });
      const payload = ticket.getPayload();
      if (!payload?.sub || !payload.email || payload.email_verified === false) {
        return res.status(401).json({ msg: 'invalid_token', error: 'Google account has no verified email' });
      }
      googleId = payload.sub;
      verifiedEmail = payload.email;
    } catch (error) {
      console.error('Token verification failed:', error);
      return res.status(401).json({ msg: 'invalid_token', error: 'Invalid Google ID token' });
    }

    // the email must belong to the Google account that signed the token
    if (email.trim().toLowerCase() !== verifiedEmail.toLowerCase()) {
      return res.status(400).json({ msg: 'invalid_request', error: 'email does not match the Google account' });
    }

    try {
      if (await userExists(googleId)) {
        return res.status(409).json({ msg: 'already_exists' });
      }

      const userId = await createUser({
        googleId,
        email: verifiedEmail,
        firstName,
        lastName,
        birthYear: year,
      });
      logInfo(`User created id: ${userId}`);

      return res.status(201).json({ msg: 'signedIn', jwt_token: createAuthToken(userId) });
    } catch (error) {
      // two sign ups for the same account at once, the unique google_id rejects the second
      if (isDuplicateEntryError(error)) {
        return res.status(409).json({ msg: 'already_exists' });
      }
      console.error('Sign up failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not create account, try again later' });
    }
  }));

  interface LoginRequestBody {
    id_token?: unknown;
  }

  /*  sign in (SignInUser): verifies the Google token and returns a JWT if the
      user exists. Responds no_user if they need to sign up first, other
      errors use the same msg codes as sign up with a description in error
   */
  app.post('/api/auth/login', asyncRoute(async (req: Request<Record<string, never>, unknown, LoginRequestBody | undefined>, res: Response) => {
    const { id_token } = req.body ?? {};
    logInfo('Received a login request');

    if (typeof id_token !== 'string' || id_token === '') {
      return res.status(400).json({ msg: 'invalid_request', error: 'id_token is required' });
    }

    let googleId: string;
    try {
      const audience = process.env.GOOGLE_CLIENT_ID ?? '';
      const ticket = await googleClient.verifyIdToken({ idToken: id_token, audience });
      const payload = ticket.getPayload();
      if (!payload?.sub) {
        return res.status(401).json({ msg: 'invalid_token', error: 'Invalid Google ID token' });
      }
      googleId = payload.sub;
    } catch (error) {
      console.error('Token verification failed:', error);
      return res.status(401).json({ msg: 'invalid_token', error: 'Invalid Google ID token' });
    }

    try {
      const user = await findUserByGoogleId(googleId);
      if (!user) {
        return res.status(404).json({ msg: 'no_user' });
      }

      logInfo(`User signed in id: ${user.userId}`);
      return res.status(200).json({ msg: 'signedIn', jwt_token: createAuthToken(user.userId) });
    } catch (error) {
      console.error('Login failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not sign in, try again later' });
    }
  }));

  /*  saves the device's Firebase token for the signed in user so the server
      can send them push notifications. requireAuth checks the JWT first
   */
  app.post('/api/register_notif', requireAuth, asyncRoute(async (req: Request, res: Response) => {
    const fcmToken: unknown = req.body?.fcm_token;
    const user = getAuthUser(res);

    if (typeof fcmToken !== 'string' || fcmToken.trim() === '' || fcmToken.length > MAX_FCM_TOKEN_LENGTH) {
      return res.status(400).json({
        msg: 'invalid_request',
        error: `fcm_token is required (max ${MAX_FCM_TOKEN_LENGTH} characters)`,
      });
    }

    try {
      await saveDeviceToken(fcmToken.trim(), user.userId);
      logInfo(`Registered notification token for user id: ${user.userId}`);
      return res.status(200).json({ msg: 'success' });
    } catch (error) {
      console.error('Registering notification token failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not register notifications, try again later' });
    }
  }));

  /*  create game: makes the game described in the body for the signed in user, who
      becomes its organizer and first player. Responds with the stored game, or
      400 invalid_request with the problem if a field is missing or invalid.
      Also sends the organizer a push notification after a few seconds to test notifications
   */
  app.post('/api/games', requireAuth, asyncRoute(async (req: Request<Record<string, never>, unknown, CreateGameBody | undefined>, res: Response) => {
    const user = getAuthUser(res);
    const parsed = parseNewGame(req.body, user.userId);
    if (!parsed.ok) {
      return res.status(400).json({ msg: 'invalid_request', error: parsed.error });
    }
    const game = parsed.value;

    try {
      const gameId = await createGame(game);
      logInfo(`Game created id: ${gameId}`);
      // read it back so the app gets the stored values (times, player count, status, ...)
      const created = await getGameById(gameId);
      if (!created) {
        throw new Error(`Game ${gameId} not found after creating it`);
      }

      // debugging: notify the organizer shortly after to check push notifications work
      setTimeout(() => {
        sendPushToUser(user.userId, {
          title: 'Game created',
          body: `${game.title} at ${game.locationName} was created`,
          data: { type: 'game_created', gameId: String(gameId) },
        }).then((devices) => { logInfo(`Debug game notification sent to ${devices} device(s)`); })
          .catch(logNotificationError);
      }, DEBUG_NOTIFICATION_DELAY_MS);

      return res.status(201).json({ msg: 'success', game: gameJson(created) });
    } catch (error) {
      console.error('Creating game failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not create game, try again later' });
    }
  }));

  /*  the signed in user's games, split into the ones they organized and the ones
      they joined, soonest first. Responds msg "no_games" if they have none yet
   */
  app.get('/api/games/mine', requireAuth, asyncRoute(async (_req: Request, res: Response) => {
    const user = getAuthUser(res);
    try {
      const { organized, joined } = await getGamesForUser(user.userId);
      if (organized.length === 0 && joined.length === 0) {
        return res.status(200).json({ msg: 'no_games' });
      }
      return res.status(200).json({
        msg: 'success',
        organized_games: organized.map(gameJson),
        joined_games: joined.map(gameJson),
      });
    } catch (error) {
      console.error('Loading games failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not load games, try again later' });
    }
  }));

  /*  discover: upcoming games near ?lat=&lng= (within MAX_DISCOVER_DISTANCE_M) that the
      signed in user can ask to join, closest first. Each has distance_m and the user's
      join_request_status (null if they never asked). Responds msg "no_games" when none
      are nearby, 400 invalid_request for missing or out of range coordinates.
      Registered before /api/games/:gameId so "discover" isn't read as an id
   */
  app.get('/api/games/discover', requireAuth, asyncRoute(async (req: Request, res: Response) => {
    const user = getAuthUser(res);
    const point = parseCoordinates(req.query.lat, req.query.lng);
    if (!point) {
      return res.status(400).json({
        msg: 'invalid_request',
        error: 'lat (-90 to 90) and lng (-180 to 180) are required numbers',
      });
    }

    try {
      const games = await getDiscoverGames(user.userId, point.latitude, point.longitude);
      if (games.length === 0) {
        return res.status(200).json({ msg: 'no_games' });
      }
      return res.status(200).json({ msg: 'success', games: games.map(discoverGameJson) });
    } catch (error) {
      console.error('Loading discover games failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not load games, try again later' });
    }
  }));

  /*  game details: the game with its organizer, players and what the signed in user
      can do with it. viewer_role is "organizer", "player" (joined) or "viewer".
      pending_requests is only sent to the organizer and join_request_status
      (null if they never asked) only to viewers, both are null otherwise.
      Responds 400 invalid_request for a malformed id, 404 not_found if no game has it.
      Registered after /api/games/mine so "mine" isn't read as an id
   */
  app.get('/api/games/:gameId', requireAuth, asyncRoute(async (req: Request<{ gameId: string }>, res: Response) => {
    const user = getAuthUser(res);
    const gameId = readGameId(req.params.gameId);
    if (gameId === null) {
      return res.status(400).json({ msg: 'invalid_request', error: 'gameId must be a positive whole number' });
    }

    try {
      const game = await getGameById(gameId);
      if (!game) {
        return res.status(404).json({ msg: 'not_found', error: 'This game does not exist' });
      }

      const players = await getGamePlayers(gameId);
      // the organizer is always a player, look them up only if that ever isn't the case
      const organizerPlayer = players.find((player) => player.userId === game.organizerId);
      const organizerUser = organizerPlayer ? null : await findUserById(game.organizerId);
      const organizer = organizerPlayer ?? (organizerUser && {
        userId: organizerUser.userId,
        firstName: organizerUser.firstName,
        lastName: organizerUser.lastName,
      });
      if (!organizer) {
        throw new Error(`Organizer ${game.organizerId} of game ${gameId} not found`);
      }

      const isPlayer = players.some((player) => player.userId === user.userId);
      const role = game.organizerId === user.userId ? 'organizer' : (isPlayer ? 'player' : 'viewer');

      return res.status(200).json({
        msg: 'success',
        game: gameJson(game),
        organizer: gamePlayerJson(organizer),
        players: players.map(gamePlayerJson),
        viewer_role: role,
        pending_requests: role === 'organizer' ? await countPendingJoinRequests(gameId) : null,
        join_request_status: role === 'viewer' ? await getJoinRequestStatus(gameId, user.userId) : null,
      });
    } catch (error) {
      console.error('Loading game details failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not load the game, try again later' });
    }
  }));

  /*  edit game: the organizer replaces the game's details. The body has the same
      fields as create game; ends_at is kept as it is when left out. Responds with the
      updated game and notifies its players and pending requesters if anything changed.
      Errors: 400 invalid_request (bad field, or max_players below the current players),
      403 forbidden (not the organizer), 404 not_found, 409 game_cancelled / game_started
   */
  app.put('/api/games/:gameId', requireAuth,
    asyncRoute(async (req: Request<{ gameId: string }, unknown, CreateGameBody | undefined>, res: Response) => {
    const user = getAuthUser(res);
    const gameId = readGameId(req.params.gameId);
    if (gameId === null) {
      return res.status(400).json({ msg: 'invalid_request', error: 'gameId must be a positive whole number' });
    }
    const parsed = parseNewGame(req.body, user.userId);
    if (!parsed.ok) {
      return res.status(400).json({ msg: 'invalid_request', error: parsed.error });
    }
    const keepEndsAt = req.body?.ends_at === undefined;

    try {
      const before = await getGameById(gameId);
      if (!before) {
        const { status, body } = gameChangeError('not_found');
        return res.status(status).json(body);
      }

      const result = await updateGame(gameId, user.userId, parsed.value, keepEndsAt);
      if (!result.ok) {
        if (result.problem === 'too_few_players') {
          return res.status(400).json({
            msg: 'invalid_request',
            error: `max_players can't be below the ${result.playerCount} players already in the game`,
          });
        }
        if (result.problem === 'ends_before_start') {
          return res.status(400).json({ msg: 'invalid_request', error: 'starts_at must be before the game ends' });
        }
        const { status, body } = gameChangeError(result.problem);
        return res.status(status).json(body);
      }

      const after = await getGameById(gameId);
      if (!after) {
        throw new Error(`Game ${gameId} not found after updating it`);
      }
      logInfo(`Game updated id: ${gameId}`);

      const changes = describeChanges(before, after);
      if (result.changed && changes.length > 0) {
        // notifications are best effort, the app gets its reply without waiting for them
        getUsersToNotify(gameId, user.userId)
          .then((userIds) => sendPushToUsers(userIds, {
            title: 'Game updated',
            body: `${after.title}: the organizer changed the ${changes.join(', ')}`,
            data: { type: 'game_updated', gameId: String(gameId) },
          }))
          .then((devices) => { logInfo(`Game ${gameId} update notification sent to ${devices} device(s)`); })
          .catch((error: unknown) => { console.error(`Notifying players of game ${gameId} failed:`, error); });
      }

      return res.status(200).json({ msg: 'success', game: gameJson(after) });
    } catch (error) {
      console.error('Updating game failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not update game, try again later' });
    }
  }));

  /*  cancel game: the organizer cancels a game that hasn't started. It stays with
      status "cancelled" so players can see what happened, and they and anyone with a
      pending join request are notified. Responds with the cancelled game.
      Errors: 400 invalid_request, 403 forbidden, 404 not_found, 409 game_cancelled / game_started
   */
  app.post('/api/games/:gameId/cancel', requireAuth, asyncRoute(async (req: Request<{ gameId: string }>, res: Response) => {
    const user = getAuthUser(res);
    const gameId = readGameId(req.params.gameId);
    if (gameId === null) {
      return res.status(400).json({ msg: 'invalid_request', error: 'gameId must be a positive whole number' });
    }

    try {
      const result = await cancelGame(gameId, user.userId);
      if (!result.ok) {
        const { status, body } = gameChangeError(result.problem);
        return res.status(status).json(body);
      }

      const game = await getGameById(gameId);
      if (!game) {
        throw new Error(`Game ${gameId} not found after cancelling it`);
      }
      logInfo(`Game cancelled id: ${gameId}`);

      getUsersToNotify(gameId, user.userId)
        .then((userIds) => sendPushToUsers(userIds, {
          title: 'Game cancelled',
          body: `${game.title} at ${game.locationName} was cancelled by the organizer`,
          data: { type: 'game_cancelled', gameId: String(gameId) },
        }))
        .then((devices) => { logInfo(`Game ${gameId} cancel notification sent to ${devices} device(s)`); })
        .catch((error: unknown) => { console.error(`Notifying players of game ${gameId} failed:`, error); });

      return res.status(200).json({ msg: 'success', game: gameJson(game) });
    } catch (error) {
      console.error('Cancelling game failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not cancel game, try again later' });
    }
  }));

  /*  join request: the signed in user asks the organizer to let them join, with the
      answers from the join request popup. Responds 201 with the pending request and
      notifies the organizer. Errors: 400 invalid_request, 404 not_found,
      409 own_game / already_player / already_requested / game_full / game_cancelled / game_started
   */
  app.post('/api/games/:gameId/join-requests', requireAuth,
    asyncRoute(async (req: Request<{ gameId: string }, unknown, JoinRequestBody | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = readGameId(req.params.gameId);
      if (gameId === null) {
        return res.status(400).json({ msg: 'invalid_request', error: 'gameId must be a positive whole number' });
      }
      const parsed = parseJoinRequest(req.body);
      if (!parsed.ok) {
        return res.status(400).json({ msg: 'invalid_request', error: parsed.error });
      }

      try {
        const result = await createJoinRequest(gameId, user.userId, parsed.value);
        if (!result.ok) {
          const { status, body } = joinRequestError(result.problem);
          return res.status(status).json(body);
        }
        logInfo(`Join request ${result.requestId} sent for game id: ${gameId}`);

        // best effort, the app gets its reply without waiting for the notification
        sendPushToUser(result.organizerId, {
          title: 'New join request',
          body: `${user.firstName} wants to join ${result.title}`,
          data: { type: 'join_request', gameId: String(gameId) },
        }).then((devices) => { logInfo(`Join request notification sent to ${devices} device(s)`); })
          .catch(logNotificationError);

        return res.status(201).json({
          msg: 'success',
          request: { request_id: result.requestId, game_id: gameId, status: 'pending' },
        });
      } catch (error) {
        console.error('Sending join request failed:', error);
        return res.status(500).json({ msg: 'server_error', error: 'Could not send your request, try again later' });
      }
    }));

  /*  the organizer's view of who asked to join: the game and its pending requests,
      newest first (an empty list when there are none).
      Errors: 400 invalid_request, 403 forbidden (not the organizer), 404 not_found
   */
  app.get('/api/games/:gameId/join-requests', requireAuth,
    asyncRoute(async (req: Request<{ gameId: string }>, res: Response) => {
    const user = getAuthUser(res);
    const gameId = readGameId(req.params.gameId);
    if (gameId === null) {
      return res.status(400).json({ msg: 'invalid_request', error: 'gameId must be a positive whole number' });
    }

    try {
      const game = await getGameById(gameId);
      if (!game) {
        return res.status(404).json({ msg: 'not_found', error: 'This game does not exist' });
      }
      if (game.organizerId !== user.userId) {
        return res.status(403).json({ msg: 'forbidden', error: 'Only the organizer can see join requests' });
      }

      const requests = await getPendingJoinRequests(gameId);
      return res.status(200).json({
        msg: 'success',
        game: gameJson(game),
        requests: requests.map(pendingJoinRequestJson),
      });
    } catch (error) {
      console.error('Loading join requests failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not load join requests, try again later' });
    }
  }));

  /*  the organizer approves or declines a pending request ({ decision: "approve" | "decline" }).
      Approving adds the requester to the game. Responds with the game after the decision
      and notifies the requester. Errors: 400 invalid_request, 403 forbidden,
      404 not_found / request_not_found, 409 game_cancelled / game_started / game_full / already_responded
   */
  app.patch('/api/games/:gameId/join-requests/:requestId', requireAuth,
    asyncRoute(async (req: Request<{ gameId: string; requestId: string }, unknown, { decision?: unknown } | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = readGameId(req.params.gameId);
      // request ids are INT UNSIGNED like game ids, so they're read the same way
      const requestId = readGameId(req.params.requestId);
      if (gameId === null || requestId === null) {
        return res.status(400).json({ msg: 'invalid_request', error: 'gameId and requestId must be positive whole numbers' });
      }
      const decision = req.body?.decision;
      if (decision !== 'approve' && decision !== 'decline') {
        return res.status(400).json({ msg: 'invalid_request', error: 'decision must be "approve" or "decline"' });
      }
      const approve = decision === 'approve';

      try {
        const result = await decideJoinRequest(gameId, requestId, user.userId, approve);
        if (!result.ok) {
          const { status, body } = decisionError(result.problem);
          return res.status(status).json(body);
        }

        const game = await getGameById(gameId);
        if (!game) {
          throw new Error(`Game ${gameId} not found after answering request ${requestId}`);
        }
        logInfo(`Join request ${requestId} ${approve ? 'approved' : 'declined'} for game id: ${gameId}`);

        // best effort, the app gets its reply without waiting for the notification
        sendPushToUser(result.requesterId, approve
          ? {
            title: 'Request approved',
            body: `You're in! You joined ${result.title}`,
            data: { type: 'join_request_approved', gameId: String(gameId) },
          }
          : {
            title: 'Request declined',
            body: `The organizer declined your request to join ${result.title}`,
            data: { type: 'join_request_declined', gameId: String(gameId) },
          }).then((devices) => { logInfo(`Join request answer sent to ${devices} device(s)`); })
          .catch(logNotificationError);

        return res.status(200).json({
          msg: 'success',
          request: { request_id: requestId, status: approve ? 'approved' : 'declined' },
          game: gameJson(game),
        });
      } catch (error) {
        console.error('Answering join request failed:', error);
        return res.status(500).json({ msg: 'server_error', error: 'Could not save your answer, try again later' });
      }
    }));
    /*GET profile
  */
  app.get('/api/users/me', requireAuth, (_req: Request, res: Response) => {
    const user = getAuthUser(res);
    return res.status(200).json({ msg: 'success', user: userJson(user)});
  });

  /*
  Update Profile
  */
  app.put('/api/users/me', requireAuth, asyncRoute(async (req: Request<Record<string, never>, unknown, ProfileBody | undefined>, res: Response) => {
    const parsed = parseProfileFields(req.body);
    if(!parsed.ok) {
      return res.status(400).json({ msg: 'invalid_request', error: parsed.error });
    }

    const { userId } = getAuthUser(res);
    try {
      await updateUserProfile(userId, parsed.value);

      const updated = await findUserById(userId);
      if(!updated) {
        return res.status(404).json({ msg: 'no_user', error: 'No account found, sign up or sign in again' });
      }
      return res.status(200).json({ msg: 'success', user: userJson(updated) });
    } catch (error) {
      console.error('Updating profile failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not update profile, try again later' });
    }
  }));

  //simple api to test connection health
  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  //get the server's public api
  app.get('/getIP4', asyncRoute(async (_req: Request, res: Response) => {
    let address = '127.0.0.1'
    try{
      logInfo('Trying fetch')
      const publicIp = await fetch('https://icanhazip.com')
          .then((res) => res.text())
          .then((text) => text.trim());
      logInfo(`GOT ${publicIp}`)
      address = publicIp
      return res.status(200).json({
        msg: address,
      });
    } catch (error) {
      return res.status(401).json({
        msg: error,
      });
    }
  }));

  //server time in 24-hour format: hh:mm:ss GMT+/-hh:mm
  app.get('/getTime', (_req, res) => {
    const now = new Date();

    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    const timeStr = `${hours}:${minutes}:${seconds}`;

    //get time offset from utc then convert to hh:mm
    const offsetMinutesTotal = now.getTimezoneOffset();
    const sign = offsetMinutesTotal <= 0 ? '+' : '-';
    const absOffset = Math.abs(offsetMinutesTotal);

    const offsetHours = String(Math.floor(absOffset / 60)).padStart(2, '0');
    const offsetMins = String(absOffset % 60).padStart(2, '0');
    const gmtOffsetStr = `GMT${sign}${offsetHours}:${offsetMins}`;

    res.status(200).json({msg:`${timeStr} ${gmtOffsetStr}`})
  })

  //returns the name of the application creator
  app.get('/getAuthorName', (_req, res) => {
    res.status(200).json({
          firstName: "Pawanpreet",
          lastName: "Padda"
      });
  });

  //standard api to route not existent endpoints
  app.use((_req, res) => {
    res.status(404).json({ error: 'Not Found' });
  });

  return app;
}
