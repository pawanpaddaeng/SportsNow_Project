import { verifyAuthToken } from '../../../src/user/auth/jwt';
import * as usersDb from '../../../src/user/db/users';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, testPlayer } from '../helpers';
import { GOOGLE_ACCOUNT, mockGoogleCheck } from './googleHelpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface POST /api/auth/login
describe('Mocked: POST /api/auth/login', () => {
  // Mocked behavior: Google's check accepts the ID token, and findUserByGoogleId finds
  //   player 2 for the Google account
  // Input: { id_token }
  // Expected status code: 200
  // Expected behavior: the user is looked up by the Google account that signed the token
  // Expected output: { msg: "signedIn", jwt_token } where the JWT signs in player 2
  test('Signs in a user who has an account', async () => {
    mockGoogleCheck();
    const player = testPlayer();
    const findUserByGoogleId = jest.spyOn(usersDb, 'findUserByGoogleId').mockResolvedValue(player);

    const response = await api.post('/api/auth/login').send({ id_token: 'google-signed-token' });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'signedIn', jwt_token: expect.any(String) });
    expect(findUserByGoogleId).toHaveBeenCalledWith(GOOGLE_ACCOUNT.sub);
    const { jwt_token: jwtToken } = response.body as { jwt_token: string };
    expect(verifyAuthToken(jwtToken)).toMatchObject({ ok: true, payload: { user_id: player.userId } });
  });

  // Mocked behavior: Google's check accepts the ID token, and findUserByGoogleId finds nobody
  // Input: { id_token } from a Google account that never signed up
  // Expected status code: 404
  // Expected behavior: no JWT is made, the app sends them to sign up
  // Expected output: { msg: "no_user" }
  test('Google account has no user yet', async () => {
    mockGoogleCheck();
    jest.spyOn(usersDb, 'findUserByGoogleId').mockResolvedValue(null);

    const response = await api.post('/api/auth/login').send({ id_token: 'google-signed-token' });

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'no_user' });
  });

  // Mocked behavior: Google's check accepts the ID token, and findUserByGoogleId throws as if
  //   the database were down
  // Input: { id_token }
  // Expected status code: 500
  // Expected behavior: the error is handled and no JWT is sent
  // Expected output: { msg: "server_error", error: "Could not sign in, try again later" }
  test('Database fails while looking up the user', async () => {
    mockGoogleCheck();
    jest.spyOn(usersDb, 'findUserByGoogleId').mockRejectedValue(new Error('database is down'));

    const response = await api.post('/api/auth/login').send({ id_token: 'google-signed-token' });

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not sign in, try again later' });
  });
});
