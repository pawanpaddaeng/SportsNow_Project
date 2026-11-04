import request from 'supertest';

import { createApp } from '../../../../src/app';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addUser, cleanUpAfterTests } from '../../helpers';

let userId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
});

cleanUpAfterTests();

// Interface GET /api/games/mine
describe('Unmocked: GET /api/games/mine', () => {
  // Input: a signed in user who has no games
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: { msg: "no_games" }
  test('No games', async () => {
    const response = await request(createApp())
      .get('/api/games/mine')
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'no_games' });
  });
});
