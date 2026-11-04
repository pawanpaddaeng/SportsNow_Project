import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool, selectRows } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let requesterId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `answer-join-request-test-badInput-${firstName}`,
    email: 'answer-join-request-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

// deleting the users also deletes their games, game players, chats and join requests
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(requesterId);
});

afterAll(async () => {
  await pool.end();
});

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
    const gameId = await createGame({
      organizerId,
      title: 'Evening Basketball',
      sport: 'basketball',
      description: null,
      startsAt: new Date(Date.now() + ONE_DAY_MS),
      endsAt: null,
      locationName: 'UBC Rec Centre',
      address: '6000 Student Union Blvd',
      latitude: 49.26,
      longitude: -123.25,
      maxPlayers: 10,
      skillLevel: 'any',
    });
    const { insertId: requestId } = await executeWrite(
      "INSERT INTO join_requests (game_id, user_id, skill_level, status) VALUES (?, ?, 'beginner', 'pending')",
      [gameId, requesterId],
    );

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
