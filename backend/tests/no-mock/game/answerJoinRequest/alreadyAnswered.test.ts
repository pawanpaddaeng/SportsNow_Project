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

// Interface PATCH /api/games/{gameId}/join-requests/{requestId}
describe('Unmocked: PATCH /api/games/{gameId}/join-requests/{requestId}', () => {
  // Input: the organizer answers the same request twice
  // Expected status code: 200 the first time, then 409
  // Expected behavior: the second answer is rejected
  // Expected output: { msg: "already_responded", error: "This request was already answered or withdrawn" }
  //   the second time
  test('Already answered', async () => {
    // 1. a game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);
    const token = createAuthToken(organizerId);

    // 2. answer it once through the interface
    const first = await request(createApp())
      .patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .set('Authorization', `Bearer ${token}`)
      .send({ decision: 'decline' });
    expect(first.status).toBe(200);

    // 3. answer it again and check that's rejected
    const second = await request(createApp())
      .patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .set('Authorization', `Bearer ${token}`)
      .send({ decision: 'approve' });
    expect(second.status).toBe(409);
    expect(second.body).toEqual({
      msg: 'already_responded',
      error: 'This request was already answered or withdrawn',
    });
  });
});
