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

// Interface POST /api/games/{gameId}/cancel
describe('Unmocked: POST /api/games/{gameId}/cancel', () => {
  // Input: the organizer cancels their game that started yesterday
  // Expected status code: 409
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "game_started", error } with the reason
  test('Game already started', async () => {
    const gameId = await addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) });

    const response = await request(createApp())
      .post(`/api/games/${gameId}/cancel`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe('game_started');
    expect((await getGameById(gameId))?.status).toBe('scheduled');
  });
});
