import request from 'supertest';

import { createApp } from '../../../src/app';
import * as gamesDb from '../../../src/game/db/games';
import type * as MockEnv from '../mockEnv';
import { cleanUpAfterTests, signIn, testGame, testOrganizer } from '../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

const GAME_ID = 42;

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/cancel
describe('Mocked: POST /api/games/{gameId}/cancel', () => {
  // Mocked behavior: the games database is replaced by one upcoming game kept in memory.
  //   getGameById returns it and cancelGame marks it cancelled, like the real UPDATE.
  //   Sign in and notifications are mocked only so the route can run
  // Input: the organizer cancels their upcoming game 42
  // Expected status code: 200
  // Expected behavior: the game goes from "scheduled" to "cancelled"
  // Expected output: { msg: "success", game } with game.status "cancelled"
  test('Organizer cancels their game', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);
    // nobody to notify after cancelling, notifications aren't checked here
    jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([]);

    // 2. make a mock game: the organizer's upcoming game, kept in memory instead of the database
    const game = testGame({ gameId: GAME_ID, organizerId: organizer.userId });
    jest.spyOn(gamesDb, 'getGameById').mockImplementation((gameId) =>
      Promise.resolve(gameId === game.gameId ? game : null));
    const cancelGame = jest.spyOn(gamesDb, 'cancelGame').mockImplementation(() => {
      game.status = 'cancelled';
      return Promise.resolve({ ok: true });
    });
    expect(game.status).toBe('scheduled');

    // 3. cancel it through the interface
    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/cancel`)
      .set('Authorization', `Bearer ${token}`);

    // 4. check it was cancelled
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: GAME_ID, status: 'cancelled' });
    expect(cancelGame).toHaveBeenCalledWith(GAME_ID, organizer.userId);
    expect(game.status).toBe('cancelled');
  });
});
