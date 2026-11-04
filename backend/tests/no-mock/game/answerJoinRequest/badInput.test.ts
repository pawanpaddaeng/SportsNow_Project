import request from 'supertest';

import { createApp } from '../../../../src/app';
import { selectRows } from '../../../../src/core/db/mysql';
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
  // Input: { decision: "maybe" } for a real request, and a valid decision with a bad request id
  // Expected status code: 400
  // Expected behavior: the real request stays pending
  // Expected output: { msg: "invalid_request", error } naming the bad input
  test.each([
    ['decision "maybe"', 'maybe', false, 'decision must be "approve" or "decline"'],
    ['a bad request id', 'approve', true, 'gameId and requestId must be positive whole numbers'],
  ] as const)('Bad input: %s', async (_name, decision, useBadRequestId, error) => {
    // 1. a game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);

    // 2. send the bad input through the interface
    const response = await request(createApp())
      .patch(`/api/games/${gameId}/join-requests/${useBadRequestId ? 'abc' : String(requestId)}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ decision });

    // 3. check it was rejected and the request is untouched
    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error });
    const requests = await selectRows<{ status: string }>(
      'SELECT status FROM join_requests WHERE request_id = ?', [requestId]);
    expect(requests).toEqual([{ status: 'pending' }]);
  });
});
