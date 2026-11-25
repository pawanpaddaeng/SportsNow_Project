import jwt from 'jsonwebtoken';
import { env } from '../../../src/core/config/env';
import { addUser, api, asUser, type Call, cleanUpAfterTests, expectSignInRequired, withToken } from '../helpers';
import { expectedProfile } from './userHelpers';

// a token signed with the server's real secret, with the contents given
function signedToken(payload: object): string {
  return jwt.sign(payload, env.jwtSecret, { algorithm: 'HS256' });
}

const NOW_SECONDS = (): number => Math.floor(Date.now() / 1000);

// a user_id no user has
const MISSING_USER_ID = 999999999;

let aliceId: number;

beforeEach(async () => {
  aliceId = await addUser('Alice');
});

cleanUpAfterTests();

// Interface GET /api/users/me
describe('Unmocked: GET /api/users/me', () => {
  // Input: no Authorization header, then a token that isn't a real JWT
  // Expected status code: 400, then 401
  // Expected behavior: no profile is read
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    await expectSignInRequired((headers) => api.get('/api/users/me').set(headers) as Call);
  });

  // Input: Alice asks for her own profile
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: { msg: "success", user } with Alice's id, email, names, birth year and
  //   when her account was created
  test('Returns the signed in user\'s profile', async () => {
    const response = await api.get('/api/users/me').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', user: expectedProfile(aliceId, 'Alice') });
  });

  // Input: tokens the sign in check must turn down: an expired one, one without user_id, one
  //   for a user that doesn't exist, one issued an hour before Alice's account was made, and an
  //   Authorization header of "Bearer " with nothing after it
  // Expected status code: 401, or 400 for the empty header
  // Expected behavior: no profile is read
  // Expected output: { msg: "expired_token" }, { msg: "invalid_token" }, { msg: "no_user" } or
  //   { msg: "invalid_request" }
  test.each([
    ['an expired token', () => signedToken({ user_id: aliceId, iat: NOW_SECONDS() - 120, exp: NOW_SECONDS() - 60 }), 401, 'expired_token'],
    ['a token without user_id', () => signedToken({ iat: NOW_SECONDS() }), 401, 'invalid_token'],
    ['a token for a user that does not exist', () => signedToken({ user_id: MISSING_USER_ID, iat: NOW_SECONDS() }), 401, 'no_user'],
    ['a token older than the account', () => signedToken({ user_id: aliceId, iat: NOW_SECONDS() - 3600 }), 401, 'no_user'],
    ['an empty Bearer header', () => '', 400, 'invalid_request'],
  ])('Rejects %s', async (_case, makeToken, status, msg) => {
    const response = await api.get('/api/users/me').set(withToken(makeToken()));

    expect(response.status).toBe(status);
    expect(response.body).toMatchObject({ msg });
  });
});
