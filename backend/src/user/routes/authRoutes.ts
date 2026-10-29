import { Router, type Request, type Response } from 'express';
import { OAuth2Client } from 'google-auth-library';
import { createAuthToken } from '../auth/jwt';
import { getAuthUser, requireAuth } from '../auth/middleware';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logError, logInfo } from '../../core/logger';
import { MAX_FCM_TOKEN_LENGTH, saveDeviceToken } from '../../core/notifications/deviceTokens';
import { createUser, findUserByGoogleId, isDuplicateEntryError, userExists } from '../db/users';
import { parseProfileFields } from '../validation/profile';

interface SignUpRequestBody {
  id_token?: unknown;
  email?: unknown;
  f_name?: unknown;
  l_name?: unknown;
  birthYear?: unknown;
}

interface LoginRequestBody {
  id_token?: unknown;
}

// the Google account that signed an id token
interface GoogleAccount {
  googleId: string;
  email: string | null;
  emailVerified: boolean;
}

// the Google account that signed idToken, or null when the token isn't valid for this app
async function verifyGoogleToken(client: OAuth2Client, idToken: string): Promise<GoogleAccount | null> {
  try {
    const ticket = await client.verifyIdToken({ idToken, audience: process.env.GOOGLE_CLIENT_ID ?? '' });
    const payload = ticket.getPayload();
    if (!payload?.sub) return null;
    return { googleId: payload.sub, email: payload.email ?? null, emailVerified: payload.email_verified !== false };
  } catch (error) {
    logError('Token verification failed:', error);
    return null;
  }
}

const INVALID_GOOGLE_TOKEN = { msg: 'invalid_token', error: 'Invalid Google ID token' };

// sign up, sign in and registering the device for push notifications
export function authRouter(): Router {
  const router = Router();
  const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

  /*  sign up: verifies the Google token, adds the user to the database and
      returns a JWT for future requests. Responds already_exists if the
      Google account already has a user, they should sign in instead
   */
  router.post('/api/auth/signup', asyncRoute('Could not create account, try again later',
    async (req: Request<Record<string, never>, unknown, SignUpRequestBody | undefined>, res: Response) => {
      const { id_token, email } = req.body ?? {};
      logInfo('Received a sign up request');

      if (typeof id_token !== 'string' || id_token === '' || typeof email !== 'string' || email === '') {
        return invalidRequest(res, 'id_token and email are required');
      }

      const profile = parseProfileFields(req.body);
      if(!profile.ok) return invalidRequest(res, profile.error);

      const account = await verifyGoogleToken(googleClient, id_token);
      if (!account) return res.status(401).json(INVALID_GOOGLE_TOKEN);
      if (!account.email || !account.emailVerified) {
        return res.status(401).json({ msg: 'invalid_token', error: 'Google account has no verified email' });
      }

      // the email must belong to the Google account that signed the token
      if (email.trim().toLowerCase() !== account.email.toLowerCase()) {
        return invalidRequest(res, 'email does not match the Google account');
      }

      if (await userExists(account.googleId)) {
        return res.status(409).json({ msg: 'already_exists' });
      }

      const { firstName, lastName, birthYear } = profile.value;
      let userId: number;
      try {
        userId = await createUser({ googleId: account.googleId, email: account.email, firstName, lastName, birthYear });
      } catch (error) {
        // two sign ups for the same account at once, the unique google_id rejects the second
        if (isDuplicateEntryError(error)) return res.status(409).json({ msg: 'already_exists' });
        throw error;
      }
      logInfo(`User created id: ${userId}`);

      return res.status(201).json({ msg: 'signedIn', jwt_token: createAuthToken(userId) });
    }));

  /*  sign in (SignInUser): verifies the Google token and returns a JWT if the
      user exists. Responds no_user if they need to sign up first, other
      errors use the same msg codes as sign up with a description in error
   */
  router.post('/api/auth/login', asyncRoute('Could not sign in, try again later',
    async (req: Request<Record<string, never>, unknown, LoginRequestBody | undefined>, res: Response) => {
      const { id_token } = req.body ?? {};
      logInfo('Received a login request');

      if (typeof id_token !== 'string' || id_token === '') {
        return invalidRequest(res, 'id_token is required');
      }

      const account = await verifyGoogleToken(googleClient, id_token);
      if (!account) return res.status(401).json(INVALID_GOOGLE_TOKEN);

      const user = await findUserByGoogleId(account.googleId);
      if (!user) {
        return res.status(404).json({ msg: 'no_user' });
      }

      logInfo(`User signed in id: ${user.userId}`);
      return res.status(200).json({ msg: 'signedIn', jwt_token: createAuthToken(user.userId) });
    }));

  /*  saves the device's Firebase token for the signed in user so the server
      can send them push notifications. requireAuth checks the JWT first
   */
  router.post('/api/register_notif', requireAuth, asyncRoute('Could not register notifications, try again later',
    async (req: Request, res: Response) => {
      const fcmToken: unknown = req.body?.fcm_token;
      const user = getAuthUser(res);

      if (typeof fcmToken !== 'string' || fcmToken.trim() === '' || fcmToken.length > MAX_FCM_TOKEN_LENGTH) {
        return invalidRequest(res, `fcm_token is required (max ${MAX_FCM_TOKEN_LENGTH} characters)`);
      }

      await saveDeviceToken(fcmToken.trim(), user.userId);
      logInfo(`Registered notification token for user id: ${user.userId}`);
      return res.status(200).json({ msg: 'success' });
    }));

  return router;
}
