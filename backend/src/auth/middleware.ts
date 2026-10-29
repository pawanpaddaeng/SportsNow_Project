import type { NextFunction, Request, Response } from 'express';
import { findUserById, type User } from '../db/users';
import { asyncRoute } from '../http/asyncRoute';
import { invalidRequest } from '../http/responses';
import { verifyAuthToken } from './jwt';

// reads the JWT from "Authorization: Bearer <token>", or from jwt_token in the json body
function readToken(req: Request): string | null {
  const header = req.get('authorization');
  if (header?.startsWith('Bearer ')) {
    const token = header.slice('Bearer '.length).trim();
    if (token) return token;
  }
  const bodyToken: unknown = req.body?.jwt_token;
  return typeof bodyToken === 'string' && bodyToken !== '' ? bodyToken : null;
}

/*
    JWT middleware for routes that need a signed in user. On success the user
    is available to the route through getAuthUser(res). Otherwise it responds:
      400 invalid_request  no token was sent
      401 expired_token    token expired, sign in again
      401 invalid_token    token is malformed or has a bad signature
      401 no_user          user was deleted, or the token was issued before the
                           account existed (user_id reused after a database reset)
      500 server_error     database error
 */
async function checkAuth(req: Request, res: Response, next: NextFunction): Promise<void> {
  const token = readToken(req);
  if (!token) {
    invalidRequest(res, 'jwt_token is required');
    return;
  }

  const result = verifyAuthToken(token);
  if (!result.ok) {
    if (result.reason === 'expired') {
      res.status(401).json({ msg: 'expired_token', error: 'Session expired, sign in again' });
    } else {
      res.status(401).json({ msg: 'invalid_token', error: 'Invalid jwt_token' });
    }
    return;
  }

  const user = await findUserById(result.payload.user_id);
  const createdAtSeconds = user ? Math.floor(user.createdAt.getTime() / 1000) : 0;
  if (!user || result.payload.iat < createdAtSeconds) {
    res.status(401).json({ msg: 'no_user', error: 'No account found, sign up or sign in again' });
    return;
  }

  res.locals.user = user;
  next();
}

// checkAuth as Express middleware (Express expects a plain function, not one returning a promise)
export const requireAuth = asyncRoute('Could not check sign in, try again later', checkAuth);

// signed in user set by requireAuth, only call from routes that use it
export function getAuthUser(res: Response): User {
  return res.locals.user as User;
}
