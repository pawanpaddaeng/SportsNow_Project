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
  // Mocked behavior: createGame is only watched, to check it doesn't run.
  //   Sign in is mocked only so the route can run
  // Input: a valid game without its title, from a signed in user
  // Expected status code: 400
  // Expected behavior: the request is rejected before anything reaches the database
  // Expected output: { msg: "invalid_request", error: "title is required (max 100 characters)" }
  test('Invalid body never reaches the database', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. watch the save
    const createGame = jest.spyOn(gamesDb, 'createGame');

    // 3. send a game with no title through the interface
    const startsAt = new Date(Date.now() + ONE_DAY_MS);
    const response = await request(createApp())
      .post('/api/games')
      .set('Authorization', `Bearer ${token}`)
      .send({
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

    // 4. check it was rejected and nothing was saved
    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'title is required (max 100 characters)' });
    expect(createGame).not.toHaveBeenCalled();
  });
});
