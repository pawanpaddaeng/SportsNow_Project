import request from 'supertest';

import { createApp } from '../../../../src/app';
import { getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, cleanUpAfterTests, ONE_DAY_MS } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/complete
describe('Unmocked: POST /api/games/{gameId}/complete', () => {
  // Input: the organizer marks their started game as completed
  // Expected status code: 200
  // Expected behavior: the game goes from "scheduled" (in progress) to "completed" in the database
  // Expected output: { msg: "success", game } with game.status "completed"
  test('Organizer marks their started game completed', async () => {
    // 1. the organizer's game that started yesterday
    const gameId = await addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) });
    expect((await getGameById(gameId))?.status).toBe('scheduled');

    // 2. mark it completed through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/complete`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    // 3. check it was completed
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: gameId, status: 'completed' });
    expect((await getGameById(gameId))?.status).toBe('completed');
  });
});
