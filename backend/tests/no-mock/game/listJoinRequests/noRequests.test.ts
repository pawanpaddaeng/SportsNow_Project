import request from 'supertest';

import { createApp } from '../../../../src/app';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface GET /api/games/{gameId}/join-requests
describe('Unmocked: GET /api/games/{gameId}/join-requests', () => {
  // Input: the organizer opens the list for their game, which nobody has asked to join
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: { msg: "success", game, requests } with requests an empty list
  test('No requests', async () => {
    const gameId = await addGame(organizerId);

    const response = await request(createApp())
      .get(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.requests).toEqual([]);
  });
});
