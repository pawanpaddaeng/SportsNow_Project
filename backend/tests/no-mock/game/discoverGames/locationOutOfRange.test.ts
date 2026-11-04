import request from 'supertest';

import { createApp } from '../../../../src/app';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addUser, cleanUpAfterTests } from '../../helpers';

let userId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
});

cleanUpAfterTests();

// Interface GET /api/games/discover
describe('Unmocked: GET /api/games/discover', () => {
  // Input: a signed in user's request with lat=95&lng=0, lat can't be over 90
  // Expected status code: 400
  // Expected behavior: the request is rejected, nothing changes
  // Expected output: { msg: "invalid_request", error: "lat (-90 to 90) and lng (-180 to 180) are required numbers" }
  test('Location out of range', async () => {
    const response = await request(createApp())
      .get('/api/games/discover')
      .query({ lat: 95, lng: 0 })
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'lat (-90 to 90) and lng (-180 to 180) are required numbers',
    });
  });
});
