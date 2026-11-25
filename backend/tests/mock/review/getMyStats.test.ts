import * as reputationDb from '../../../src/review/db/reputation';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/users/me/stats
describe('Mocked: GET /api/users/me/stats', () => {
  // Mocked behavior: getReputationScore throws, as when the database is down
  // Input: a signed in player asks for their stats
  // Expected status code: 500
  // Expected behavior: the error is handled and the server keeps running
  // Expected output: { msg: "server_error", error: "Could not load your rating, try again later" }
  test('Database fails', async () => {
    const token = signIn(testPlayer());
    const getReputationScore = jest.spyOn(reputationDb, 'getReputationScore')
      .mockRejectedValue(new Error('Forced error'));

    const response = await api.get('/api/users/me/stats').set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load your rating, try again later' });
    expect(getReputationScore).toHaveBeenCalledTimes(1);
  });
});