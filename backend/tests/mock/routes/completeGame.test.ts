import request from 'supertest';

import { createApp } from '../../../src/app';
import * as gamesDb from '../../../src/game/db/games';
import type * as MockEnv from '../mockEnv';
import { cleanUpAfterTests, signIn, testGame, testOrganizer } from '../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

const GAME_ID = 42;

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/complete
describe('Mocked: POST /api/games/{gameId}/complete', () => {
  // Mocked behavior: the games database is replaced by one game kept in memory that
  //   has started. getGameById returns it and completeGame marks it completed, like
  //   the real UPDATE. Sign in and notifications are mocked only so the route can run
  // Input: the organizer marks their started game 42 as completed
  // Expected status code: 200
  // Expected behavior: the game goes from "scheduled" (in progress) to "completed"
  // Expected output: { msg: "success", game } with game.status "completed"
  test('Organizer marks their started game completed', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);
    // nobody to notify after completing, notifications aren't checked here
    jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([]);

    // 2. make a mock game: the organizer's game that has started (in progress), kept in memory
    const game = testGame({
      gameId: GAME_ID,
      organizerId: organizer.userId,
      startsAt: '2026-01-06T18:00:00Z',
      hasStarted: true,
      createdAt: '2026-01-01T00:00:00Z',
    });
    jest.spyOn(gamesDb, 'getGameById').mockImplementation((gameId) =>
      Promise.resolve(gameId === game.gameId ? game : null));
    const completeGame = jest.spyOn(gamesDb, 'completeGame').mockImplementation(() => {
      game.status = 'completed';
      return Promise.resolve({ ok: true });
    });
    expect(game.status).toBe('scheduled');

    // 3. mark it completed through the interface
    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/complete`)
      .set('Authorization', `Bearer ${token}`);

    // 4. check it was completed
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: GAME_ID, status: 'completed' });
    expect(completeGame).toHaveBeenCalledWith(GAME_ID, organizer.userId);
    expect(game.status).toBe('completed');
  });
});
