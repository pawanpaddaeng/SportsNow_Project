import request from 'supertest';

import { createApp } from '../../../../src/app';
import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testPlayer } from '../../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const GAME_ID = 42;

cleanUpAfterTests();

// Interface GET /api/games/{gameId}
describe('Mocked: GET /api/games/{gameId}', () => {
  // Mocked behavior: getGameById throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: a signed in user opens game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not load the game, try again later" }
  test('Database fails', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testPlayer());

    // 2. make reading the game fail
    const getGameById = jest.spyOn(gamesDb, 'getGameById').mockRejectedValue(new Error('database down'));

    // 3. open the game through the interface
    const response = await request(createApp())
      .get(`/api/games/${GAME_ID}`)
      .set('Authorization', `Bearer ${token}`);

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load the game, try again later' });
    expect(getGameById).toHaveBeenCalledWith(GAME_ID);
  });
});
