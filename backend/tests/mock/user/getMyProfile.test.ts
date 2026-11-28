import * as usersDb from '../../../src/user/db/users';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/users/me
describe('Mocked: GET /api/users/me', () => {
  // Mocked behavior: findUserById throws as if the database were down
  // Input: player 2 asks for their profile
  // Expected status code: 500
  // Expected behavior: the sign in check fails, so no profile is sent
  // Expected output: { msg: "server_error", error: "Could not check sign in, try again later" }
  test('Database fails while checking the sign in', async () => {
    const token = signIn(testPlayer());
    jest.spyOn(usersDb, 'findUserById').mockRejectedValue(new Error('database is down'));

    const response = await api.get('/api/users/me').set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not check sign in, try again later' });
  });
});
