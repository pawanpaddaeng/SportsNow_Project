import express, { type Express, type Request, type Response } from 'express';
import { OAuth2Client } from 'google-auth-library';
import WebSocket from 'ws'
import { createAuthToken } from './auth/jwt';
import { getAuthUser, requireAuth } from './auth/middleware';
import { MAX_FCM_TOKEN_LENGTH, saveDeviceToken } from './db/deviceTokens';
import { createGame, gameJson, getGameById } from './db/games';
import { parseNewGame, type CreateGameBody } from './validation/newGame';
import { sendPushToUser } from './notifications/push';

// how long after creating a game the debug notification is sent
const DEBUG_NOTIFICATION_DELAY_MS = 5000;
import { createUser, findUserByGoogleId, isDuplicateEntryError, userExists } from './db/users';

// youngest and oldest ages allowed to sign up, matches the app's birth year picker
const MIN_AGE = 19;
const MAX_AGE = 100;
const MAX_NAME_LENGTH = 100;

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
    console.log("CLIENT CONNECTED TO WEBSOCKET")
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

  app.post('/api/auth/google', async (req: Request<{}, {}, GoogleAuthRequestBody>, res: Response) => {
    const { idToken } = req.body;
    console.log("Received an authorization request");
    

    if (!idToken) {
      return res.status(400).json({ error: 'idToken is required' });
    }

    try {
      // Verify the token with Google
      const ticket = await googleClient.verifyIdToken({
        idToken: idToken,
        audience: process.env.GOOGLE_CLIENT_ID,
      });

      const payload = ticket.getPayload();

      if (!payload) {
        return res.status(401).json({ error: 'Invalid token payload' });
      }

      const firstName = payload.given_name || '';
      const lastName = payload.family_name || '';
      console.log(`Successfully authenticated user`);

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
  });

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
  app.post('/api/auth/signup', async (req: Request<{}, {}, SignUpRequestBody>, res: Response) => {
    const { id_token, email, f_name, l_name, birthYear } = req.body ?? {};
    console.log("Received a sign up request");

    // check the request fields before contacting Google
    const firstName = typeof f_name === 'string' ? f_name.trim() : '';
    const lastName = typeof l_name === 'string' ? l_name.trim() : '';
    const year = typeof birthYear === 'number' || typeof birthYear === 'string'
      ? Number(birthYear) : NaN;
    const currentYear = new Date().getFullYear();

    if (typeof id_token !== 'string' || id_token === '' || typeof email !== 'string' || email === '') {
      return res.status(400).json({ msg: 'invalid_request', error: 'id_token and email are required' });
    }
    if (!firstName || !lastName || firstName.length > MAX_NAME_LENGTH || lastName.length > MAX_NAME_LENGTH) {
      return res.status(400).json({
        msg: 'invalid_request',
        error: `f_name and l_name are required (max ${MAX_NAME_LENGTH} characters)`,
      });
    }
    if (!Number.isInteger(year) || year > currentYear - MIN_AGE || year < currentYear - MAX_AGE) {
      return res.status(400).json({
        msg: 'invalid_request',
        error: `birthYear must be between ${currentYear - MAX_AGE} and ${currentYear - MIN_AGE}`,
      });
    }

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
      console.log(`User created id:`, userId);

      return res.status(201).json({ msg: 'signedIn', jwt_token: createAuthToken(userId) });
    } catch (error) {
      // two sign ups for the same account at once, the unique google_id rejects the second
      if (isDuplicateEntryError(error)) {
        return res.status(409).json({ msg: 'already_exists' });
      }
      console.error('Sign up failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not create account, try again later' });
    }
  });

  interface LoginRequestBody {
    id_token?: unknown;
  }

  /*  sign in (SignInUser): verifies the Google token and returns a JWT if the
      user exists. Responds no_user if they need to sign up first, other
      errors use the same msg codes as sign up with a description in error
   */
  app.post('/api/auth/login', async (req: Request<{}, {}, LoginRequestBody>, res: Response) => {
    const { id_token } = req.body ?? {};
    console.log("Received a login request");

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

      console.log(`User signed in id:`, user.userId);
      return res.status(200).json({ msg: 'signedIn', jwt_token: createAuthToken(user.userId) });
    } catch (error) {
      console.error('Login failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not sign in, try again later' });
    }
  });

  /*  saves the device's Firebase token for the signed in user so the server
      can send them push notifications. requireAuth checks the JWT first
   */
  app.post('/api/register_notif', requireAuth, async (req: Request, res: Response) => {
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
      console.log(`Registered notification token for user id:`, user.userId);
      return res.status(200).json({ msg: 'success' });
    } catch (error) {
      console.error('Registering notification token failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not register notifications, try again later' });
    }
  });

  /*  create game: makes the game described in the body for the signed in user, who
      becomes its organizer and first player. Responds with the stored game, or
      400 invalid_request with the problem if a field is missing or invalid.
      Also sends the organizer a push notification after a few seconds to test notifications
   */
  app.post('/api/games', requireAuth, async (req: Request<{}, {}, CreateGameBody>, res: Response) => {
    const user = getAuthUser(res);
    const parsed = parseNewGame(req.body, user.userId);
    if (!parsed.ok) {
      return res.status(400).json({ msg: 'invalid_request', error: parsed.error });
    }
    const game = parsed.value;

    try {
      const gameId = await createGame(game);
      console.log(`Game created id:`, gameId);
      // read it back so the app gets the stored values (times, player count, status, ...)
      const created = await getGameById(gameId);
      if (!created) {
        throw new Error(`Game ${gameId} not found after creating it`);
      }

      // debugging: notify the organizer shortly after to check push notifications work
      setTimeout(() => {
        void sendPushToUser(user.userId, {
          title: 'Game created',
          body: `${game.title} at ${game.locationName} was created`,
          data: { type: 'game_created', gameId: String(gameId) },
        }).then((devices) => console.log(`Debug game notification sent to ${devices} device(s)`));
      }, DEBUG_NOTIFICATION_DELAY_MS);

      return res.status(201).json({ msg: 'success', game: gameJson(created) });
    } catch (error) {
      console.error('Creating game failed:', error);
      return res.status(500).json({ msg: 'server_error', error: 'Could not create game, try again later' });
    }
  });

  //simple api to test connection health
  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  //get the server's public api
  app.get('/getIP4', async (_req, res) => {
    let address = '127.0.0.1'
    try{
      console.log("Trying fetch")
      const publicIp = await fetch('https://icanhazip.com')
          .then((res) => res.text())
          .then((text) => text.trim());
      console.log("GOT" + publicIp)
      address = publicIp
      return res.status(200).json({
        msg: address,
      });
    } catch (error) {
      return res.status(401).json({
        msg: error,
      });
    }
  });

  //server time in 24-hour format: hh:mm:ss GMT+/-hh:mm
  app.get('/getTime', async (_req, res) => {
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
  app.get('/getAuthorName', async(_req, res) => {
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
