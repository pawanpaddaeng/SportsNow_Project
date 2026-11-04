import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, cleanUpAfterTests, ONE_DAY_MS } from '../../helpers';

let organizerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: a user asks to join a cancelled game, and a game that already started
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "game_cancelled" } and { msg: "game_started" }
  test.each([
    ['cancelled', 'game_cancelled', async () => {
      const gameId = await addGame(organizerId);
      await executeWrite("UPDATE games SET status = 'cancelled' WHERE game_id = ?", [gameId]);
      return gameId;
    }],
    ['started', 'game_started', () => addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) })],
  ] as const)('Asking to join a %s game', async (_name, msg, setUpGame) => {
    const gameId = await setUpGame();

    const response = await request(createApp())
      .post(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(requesterId)}`)
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
  });
});
