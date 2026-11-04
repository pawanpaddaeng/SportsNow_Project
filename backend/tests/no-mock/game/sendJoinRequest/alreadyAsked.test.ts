import request from 'supertest';

import { createApp } from '../../../../src/app';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addJoinRequest, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: a second request while the user's first is still pending
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "already_requested", error } saying to wait for the organizer
  test('Already asked', async () => {
    const gameId = await addGame(organizerId);
    await addJoinRequest(gameId, requesterId);

    const response = await request(createApp())
      .post(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(requesterId)}`)
      .send({ skill_level: 'advanced' });

    expect(response.status).toBe(409);
    expect(response.body).toEqual({
      msg: 'already_requested',
      error: 'You already asked to join this game, wait for the organizer to respond',
    });
  });
});
