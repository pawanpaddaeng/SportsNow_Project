import request from 'supertest';

import { createApp } from '../../../../src/app';
import { getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let otherUserId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  otherUserId = await addUser('Omar');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/cancel
describe('Unmocked: POST /api/games/{gameId}/cancel', () => {
  // Input: a user who isn't the organizer cancels the game
  // Expected status code: 403
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "forbidden", error: "Only the organizer can change this game" }
  test('Not the organizer', async () => {
    const gameId = await addGame(organizerId);

    const response = await request(createApp())
      .post(`/api/games/${gameId}/cancel`)
      .set('Authorization', `Bearer ${createAuthToken(otherUserId)}`);

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can change this game' });
    expect((await getGameById(gameId))?.status).toBe('scheduled');
  });
});
