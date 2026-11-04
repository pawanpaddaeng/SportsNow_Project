import request from 'supertest';

import { createApp } from '../../../../src/app';
import { selectRows } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: the organizer asks to join their own game
  // Expected status code: 409
  // Expected behavior: nothing is saved
  // Expected output: { msg: "own_game", error: "You organize this game" }
  test('Own game', async () => {
    const gameId = await addGame(organizerId);

    const response = await request(createApp())
      .post(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'own_game', error: 'You organize this game' });
    const rows = await selectRows<{ request_id: number }>(
      'SELECT request_id FROM join_requests WHERE game_id = ?', [gameId]);
    expect(rows).toEqual([]);
  });
});
