import { Router, type Request, type Response } from 'express';
import { OAuth2Client } from 'google-auth-library';
import { createAuthToken } from '../auth/jwt';
import { getAuthUser, requireAuth } from '../auth/middleware';
import { MAX_FCM_TOKEN_LENGTH, saveDeviceToken } from '../db/deviceTokens';
import { createUser, findUserByGoogleId, isDuplicateEntryError, userExists } from '../db/users';
import { asyncRoute } from '../http/asyncRoute';
import { logInfo } from '../logger';
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

// sign up, sign in and registering the device for push notifications
export function authRouter(): Router {
  const router = Router();
  const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

  /*  sign up: verifies the Google token, adds the user to the database and
      returns a JWT for future requests. Responds already_exists if the
      Google account already has a user, they should sign in instead
   */
  router.post('/api/auth/signup', asyncRoute(async (req: Request<Record<string, never>, unknown, SignUpRequestBody | undefined>, res: Response) => {
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
        audience: process.env.GOOGLE_CLIENT_ID ?? '',
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

  /*  sign in (SignInUser): verifies the Google token and returns a JWT if the
      user exists. Responds no_user if they need to sign up first, other
      errors use the same msg codes as sign up with a description in error
   */
  router.post('/api/auth/login', asyncRoute(async (req: Request<Record<string, never>, unknown, LoginRequestBody | undefined>, res: Response) => {
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
  router.post('/api/register_notif', requireAuth, asyncRoute(async (req: Request, res: Response) => {
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

  return router;
}
