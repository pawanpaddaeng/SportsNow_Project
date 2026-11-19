import type { Express } from 'express';
import jwt from 'jsonwebtoken';

import { env } from '../../../src/core/config/env';
import { selectRows } from '../../../src/core/db/mysql';
import { createAuthToken } from '../../../src/user/auth/jwt';
import { type Call } from '../../no-mock/helpers';
import { api, type AuthHeader, validGameBody, withToken } from '../../sharedHelpers';

// setup and checks shared by the NFR 1 (Security) tests

export type Method = 'get' | 'post' | 'put' | 'patch' | 'delete';

/*  one endpoint. body is what the app would send, so a request that got past the sign in
    check would really change something the tests can see
 */
export interface Route {
  method: Method;
  path: string;
  body?: object;
}

// the only routes anyone can call without signing in
export const PUBLIC_ROUTES: Route[] = [
  { method: 'post', path: '/api/auth/signup' },
  { method: 'post', path: '/api/auth/login' },
  { method: 'get', path: '/health' },
];

// every route that needs a signed in user, in the order app.ts registers them
export const PROTECTED_ROUTES: Route[] = [
  { method: 'post', path: '/api/register_notif', body: { fcm_token: 'attacker-device' } },
  { method: 'post', path: '/api/games', body: validGameBody({ title: 'Made by an attacker' }) },
  { method: 'get', path: '/api/games/mine' },
  { method: 'get', path: '/api/games/discover' },
  { method: 'get', path: '/api/games/:gameId' },
  { method: 'put', path: '/api/games/:gameId', body: validGameBody({ title: 'Changed by an attacker' }) },
  { method: 'post', path: '/api/games/:gameId/cancel' },
  { method: 'post', path: '/api/games/:gameId/complete' },
  { method: 'post', path: '/api/games/:gameId/leave' },
  { method: 'delete', path: '/api/games/:gameId/players/:userId' },
  { method: 'post', path: '/api/games/:gameId/join-requests', body: { skill_level: 'beginner' } },
  { method: 'get', path: '/api/games/:gameId/join-requests' },
  { method: 'patch', path: '/api/games/:gameId/join-requests/:requestId', body: { decision: 'approve' } },
  { method: 'post', path: '/api/games/:gameId/invites', body: { friend_ids: [1] } },
  { method: 'get', path: '/api/users/search' },
  { method: 'get', path: '/api/friends' },
  { method: 'get', path: '/api/friends/requests' },
  { method: 'post', path: '/api/friends/requests', body: { user_id: 1 } },
  { method: 'patch', path: '/api/friends/requests/:requestId', body: { decision: 'accept' } },
  { method: 'delete', path: '/api/friends/:userId' },
  { method: 'get', path: '/api/users/me' },
  { method: 'put', path: '/api/users/me', body: { f_name: 'Changed', l_name: 'ByAttacker', birthYear: 1990 } },
  { method: 'delete', path: '/api/users/me' },
  { method: 'get', path: '/api/chats/:chatId/messages' },
  { method: 'get', path: '/api/games/:gameId/ratings' },
  { method: 'post', path: '/api/games/:gameId/ratings', body: { rated_user_id: 1, punctuality: 1, sportsmanship: 1, skill: 1, reliability: 1 } },
  { method: 'get', path: '/api/users/me/stats' },
  { method: 'get', path: '/api/users/:userId' },
  { method: 'get', path: '/api/notifications' },
  { method: 'patch', path: '/api/notifications/read' },
];

// "get /api/games/:gameId", to compare and print routes
export function routeName(route: Route): string {
  return `${route.method} ${route.path}`;
}

// the shape of Express 5's router stack that listRegisteredRoutes reads
interface RouterLayer {
  route?: { path: string; methods: Record<string, boolean> };
  handle?: { stack?: RouterLayer[] };
}

/*  every route the app really registered, read from Express's router, including the
    routers app.ts mounts with app.use. Sorted, as routeName gives them
 */
export function listRegisteredRoutes(app: Express): string[] {
  const names: string[] = [];
  const addRoutes = (layers: RouterLayer[]): void => {
    for (const layer of layers) {
      if (layer.route) {
        const { path, methods } = layer.route;
        for (const method of Object.keys(methods)) names.push(`${method} ${path}`);
      } else if (layer.handle?.stack) {
        addRoutes(layer.handle.stack);
      }
    }
  };
  addRoutes((app as unknown as { router: { stack: RouterLayer[] } }).router.stack);
  return names.sort();
}

// the ids put in place of a route's :params
export interface RouteIds {
  gameId: number;
  userId: number;
  chatId: number;
  joinRequestId: number;
  friendRequestId: number;
}

// the route's path with its :params filled in, :requestId is a friend request under /api/friends
export function fillPath(path: string, ids: RouteIds): string {
  const requestId = path.startsWith('/api/friends') ? ids.friendRequestId : ids.joinRequestId;
  return path
    .replace(':gameId', String(ids.gameId))
    .replace(':userId', String(ids.userId))
    .replace(':chatId', String(ids.chatId))
    .replace(':requestId', String(requestId));
}

// calls the route at that path with the headers given, and its body if it has one
export function callRoute(route: Route, path: string, headers: AuthHeader): Call {
  const body = route.body ?? {};
  switch (route.method) {
    case 'get': return api.get(path).set(headers) as Call;
    case 'post': return api.post(path).set(headers).send(body) as Call;
    case 'put': return api.put(path).set(headers).send(body) as Call;
    case 'patch': return api.patch(path).set(headers).send(body) as Call;
    case 'delete': return api.delete(path).set(headers) as Call;
  }
}

const nowSeconds = (): number => Math.floor(Date.now() / 1000);

function base64url(value: object): string {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

// a token for the user signed with a secret that isn't the server's, as an attacker would make
export function forgedToken(userId: number): string {
  return jwt.sign({ user_id: userId, iat: nowSeconds() }, 'not-the-server-secret',
    { algorithm: 'HS256', expiresIn: '30d' });
}

// a token for the user with no signature at all ("alg": "none")
export function unsignedToken(userId: number): string {
  return `${base64url({ alg: 'none', typ: 'JWT' })}.${base64url({ user_id: userId, iat: nowSeconds() })}.`;
}

// a token the server really signed for the user, that expired a minute ago
export function expiredToken(userId: number): string {
  const now = nowSeconds();
  return jwt.sign({ user_id: userId, iat: now - 3600, exp: now - 60 }, env.jwtSecret, { algorithm: 'HS256' });
}

// a sign in the server must turn down, and how it must answer
export interface BadSignIn {
  name: string;
  headers: AuthHeader;
  status: number;
  msg: string;
}

/*  every kind of sign in the server must turn down, all aimed at the victim's account.
    deletedUserId is a user who was deleted after their token was made
 */
export function badSignIns(victimId: number, deletedUserId: number): BadSignIn[] {
  return [
    { name: 'no token', headers: {}, status: 400, msg: 'invalid_request' },
    { name: 'not a JWT', headers: withToken('not.a.jwt'), status: 401, msg: 'invalid_token' },
    { name: 'forged signature', headers: withToken(forgedToken(victimId)), status: 401, msg: 'invalid_token' },
    { name: 'no signature', headers: withToken(unsignedToken(victimId)), status: 401, msg: 'invalid_token' },
    { name: 'expired', headers: withToken(expiredToken(victimId)), status: 401, msg: 'expired_token' },
    { name: 'deleted account', headers: withToken(createAuthToken(deletedUserId)), status: 401, msg: 'no_user' },
  ];
}

/*  everything saved about the user, their game and its chat that a request could change.
    Compared before and after, so a turned down request is shown to have changed nothing
 */
export async function savedState(userId: number, gameId: number, chatId: number): Promise<object> {
  return {
    user: await selectRows('SELECT first_name, last_name, birth_year FROM users WHERE user_id = ?', [userId]),
    games: await selectRows(
      'SELECT game_id, title, status, starts_at, max_players FROM games WHERE organizer_id = ? ORDER BY game_id',
      [userId]),
    players: await selectRows('SELECT user_id FROM game_players WHERE game_id = ? ORDER BY user_id', [gameId]),
    joinRequests: await selectRows(
      'SELECT request_id, status FROM join_requests WHERE game_id = ? ORDER BY request_id', [gameId]),
    friendRequests: await selectRows(
      'SELECT request_id, status FROM friend_requests WHERE sender_id = ? OR receiver_id = ? ORDER BY request_id',
      [userId, userId]),
    messages: await selectRows('SELECT message_id FROM chat_messages WHERE chat_id = ? ORDER BY message_id', [chatId]),
    notifications: await selectRows(
      'SELECT notification_id, read_at FROM notifications WHERE user_id = ? ORDER BY notification_id', [userId]),
    deviceTokens: await selectRows('SELECT token FROM device_tokens WHERE user_id = ? ORDER BY token', [userId]),
  };
}
