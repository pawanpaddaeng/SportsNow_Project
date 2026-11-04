import request from 'supertest';

import { createApp } from '../../../../src/app';
import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testOrganizer } from '../../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const TWO_HOURS_MS = 2 * 60 * 60 * 1000;

cleanUpAfterTests();

// Interface POST /api/games
describe('Mocked: POST /api/games', () => {
  // Mocked behavior: createGame throws, as if the database failed while saving.
  //   Sign in is mocked only so the route can run
  // Input: a valid game from a signed in user
  // Expected status code: 500
  // Expected behavior: the error is handled and the route stops, so the game is never read back
  // Expected output: { msg: "server_error", error: "Could not create game, try again later" }
  test('Database fails while saving', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. make saving fail
    const createGame = jest.spyOn(gamesDb, 'createGame').mockRejectedValue(new Error('database down'));
    const getGameById = jest.spyOn(gamesDb, 'getGameById');

    // 3. create a valid game starting tomorrow through the interface
    const startsAt = new Date(Date.now() + ONE_DAY_MS);
    const response = await request(createApp())
      .post('/api/games')
      .set('Authorization', `Bearer ${token}`)
      .send({
        title: 'Evening Basketball',
        sport: 'basketball',
        description: 'Friendly game',
        starts_at: startsAt.toISOString(),
        ends_at: new Date(startsAt.getTime() + TWO_HOURS_MS).toISOString(),
        location_name: 'UBC Rec Centre',
        address: '6000 Student Union Blvd',
        latitude: 49.26,
        longitude: -123.25,
        max_players: 10,
        skill_level: 'any',
      });

    // 4. check the error reply, and that the route stopped at the failed save
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not create game, try again later' });
    expect(createGame).toHaveBeenCalledTimes(1);
    expect(getGameById).not.toHaveBeenCalled();
  });
});
