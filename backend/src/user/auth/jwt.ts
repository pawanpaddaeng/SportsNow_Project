import jwt from 'jsonwebtoken';
import { env } from '../../core/config/env';

// how long a token stays valid before the app has to sign in again
const TOKEN_LIFETIME = '30d';

/*  contents of the JWT given to the app:
    user_id identifies the user in later requests, iat is when the token was
    issued (seconds since 1970) so tokens older than the user's created_at can
    be rejected. exp is added by jsonwebtoken from TOKEN_LIFETIME
 */
export interface AuthTokenPayload {
  user_id: number;
  iat: number;
}

export function createAuthToken(userId: number): string {
  const payload: AuthTokenPayload = {
    user_id: userId,
    iat: Math.floor(Date.now() / 1000),
  };
  return jwt.sign(payload, env.jwtSecret, { algorithm: 'HS256', expiresIn: TOKEN_LIFETIME });
}

export type VerifyTokenResult =
  | { ok: true; payload: AuthTokenPayload }
  | { ok: false; reason: 'expired' | 'invalid' };

// checks the token's signature and expiry, and that it has the fields createAuthToken puts in
export function verifyAuthToken(token: string): VerifyTokenResult {
  try {
    const decoded = jwt.verify(token, env.jwtSecret, { algorithms: ['HS256'] });
    if (typeof decoded === 'string' || typeof decoded.user_id !== 'number' || typeof decoded.iat !== 'number') {
      return { ok: false, reason: 'invalid' };
    }
    return { ok: true, payload: { user_id: decoded.user_id, iat: decoded.iat } };
  } catch (error) {
    if (error instanceof jwt.TokenExpiredError) {
      return { ok: false, reason: 'expired' };
    }
    return { ok: false, reason: 'invalid' };
  }
}
