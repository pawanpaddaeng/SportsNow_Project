import * as badgesDb from '../../../src/review/db/badges';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/users/me/badges
describe('Mocked: GET /api/users/me/badges', () => {
  // Mocked behavior: getBadges throws, as when the database is down
  // Input: a signed in player asks for their badges
  // Expected status code: 500
  // Expected behavior: the error is handled and the server keeps running
  // Expected output: { msg: "server_error", error: "Could not load your badges, try again later" }
  test('Database fails', async () => {
    const token = signIn(testPlayer());
    const getBadges = jest.spyOn(badgesDb, 'getBadges').mockRejectedValue(new Error('Forced error'));

    const response = await api.get('/api/users/me/badges').set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load your badges, try again later' });
    expect(getBadges).toHaveBeenCalledTimes(1);
  });
});
