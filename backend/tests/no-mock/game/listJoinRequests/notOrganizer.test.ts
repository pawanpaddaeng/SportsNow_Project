import request from 'supertest';

import { createApp } from '../../../../src/app';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let otherUserId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  otherUserId = await addUser('Omar');
});

cleanUpAfterTests();

// Interface GET /api/games/{gameId}/join-requests
describe('Unmocked: GET /api/games/{gameId}/join-requests', () => {
  // Input: a user who isn't the organizer opens the list
  // Expected status code: 403
  // Expected behavior: the request is rejected, nothing changes
  // Expected output: { msg: "forbidden", error: "Only the organizer can see join requests" }
  test('Not the organizer', async () => {
    const gameId = await addGame(organizerId);

    const response = await request(createApp())
      .get(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(otherUserId)}`);

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can see join requests' });
  });
});
