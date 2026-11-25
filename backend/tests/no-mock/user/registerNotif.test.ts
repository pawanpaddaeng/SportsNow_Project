import { selectRows } from '../../../src/core/db/mysql';
import { createAuthToken } from '../../../src/user/auth/jwt';
import { addUser, api, asUser, type Call, cleanUpAfterTests, expectSignInRequired } from '../helpers';

let aliceId: number;

beforeEach(async () => {
  aliceId = await addUser('Alice');
});

cleanUpAfterTests();

// the device tokens really saved for the user
async function getSavedTokens(userId: number): Promise<string[]> {
  const rows = await selectRows<{ token: string }>('SELECT token FROM device_tokens WHERE user_id = ?', [userId]);
  return rows.map((row) => row.token);
}

// Interface POST /api/register_notif
describe('Unmocked: POST /api/register_notif', () => {
  // Input: no Authorization header, then a token that isn't a real JWT
  // Expected status code: 400, then 401
  // Expected behavior: no device token is saved
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    const body = { fcm_token: `no-mock-register-notif-${aliceId}` };

    await expectSignInRequired((headers) => api.post('/api/register_notif').set(headers).send(body) as Call);

    expect(await getSavedTokens(aliceId)).toEqual([]);
  });

  // Input: Alice sends her device's Firebase token, with spaces around it
  // Expected status code: 200
  // Expected behavior: the token is saved for Alice without the spaces, so the server can send
  //   her notifications
  // Expected output: { msg: "success" }
  test('Saves the trimmed device token for the signed in user', async () => {
    const fcmToken = `no-mock-register-notif-${aliceId}`;

    const response = await api.post('/api/register_notif').set(asUser(aliceId)).send({ fcm_token: `  ${fcmToken} ` });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(await getSavedTokens(aliceId)).toEqual([fcmToken]);
  });

  // Input: no fcm_token, then an fcm_token of only spaces
  // Expected status code: 400
  // Expected behavior: no device token is saved
  // Expected output: { msg: "invalid_request", error: "fcm_token is required (max 512 characters)" }
  test.each([
    ['no fcm_token', {}],
    ['an fcm_token of only spaces', { fcm_token: '   ' }],
    ['an fcm_token over 512 characters', { fcm_token: 'x'.repeat(513) }],
  ])('Rejects a missing or too long device token: %s', async (_case, body) => {
    const response = await api.post('/api/register_notif').set(asUser(aliceId)).send(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'fcm_token is required (max 512 characters)' });
    expect(await getSavedTokens(aliceId)).toEqual([]);
  });

  // Input: no Authorization header, with Alice's JWT sent as jwt_token in the json body instead
  // Expected status code: 200
  // Expected behavior: the sign in check reads the token from the body, and the token is saved for Alice
  // Expected output: { msg: "success" }
  test('Accepts the JWT sent in the body as jwt_token', async () => {
    const fcmToken = `no-mock-register-notif-body-${aliceId}`;
    const response = await api.post('/api/register_notif').send({ jwt_token: createAuthToken(aliceId), fcm_token: fcmToken });

    expect(response.status).toBe(200);
    expect(await getSavedTokens(aliceId)).toEqual([fcmToken]);
  });
});
