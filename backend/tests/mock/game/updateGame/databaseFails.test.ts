import request from 'supertest';

import { createApp } from '../../../../src/app';
import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testGame, testOrganizer } from '../../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const GAME_ID = 42;

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Mocked: PUT /api/games/{gameId}', () => {
  // Mocked behavior: getGameById returns the game and updateGame throws, as if the database
  //   failed while saving. Sign in is mocked only so the route can run
  // Input: the organizer edits their game
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not update game, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. make saving fail
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(
      testGame({ gameId: GAME_ID, organizerId: organizer.userId }));
    const updateGame = jest.spyOn(gamesDb, 'updateGame').mockRejectedValue(new Error('database down'));

    // 3. edit it through the interface
    const response = await request(createApp())
      .put(`/api/games/${GAME_ID}`)
      .set('Authorization', `Bearer ${token}`)
      .send({
        title: 'New Title',
        sport: 'soccer',
        starts_at: new Date(Date.now() + ONE_DAY_MS).toISOString(),
        location_name: 'Jericho Park',
        address: '3941 Point Grey Rd, Vancouver',
        latitude: 49.2722,
        longitude: -123.1957,
        max_players: 10,
        skill_level: 'any',
      });

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not update game, try again later' });
    expect(updateGame).toHaveBeenCalledTimes(1);
  });
});
