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
  // Input: the organizer answers one game's id with a request id from their other game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "request_not_found", error: "This join request does not exist" }
  test("Request doesn't exist", async () => {
    // 1. two games, and a request for the second one
    const gameId = await addGame(organizerId, { title: 'First Game' });
    const otherGameId = await addGame(organizerId, { title: 'Second Game' });
    const otherRequestId = await addJoinRequest(otherGameId, requesterId);

    // 2. answer it under the first game through the interface
    const response = await request(createApp())
      .patch(`/api/games/${gameId}/join-requests/${otherRequestId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ decision: 'approve' });

    // 3. check it wasn't found
    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'request_not_found', error: 'This join request does not exist' });
  });
});
