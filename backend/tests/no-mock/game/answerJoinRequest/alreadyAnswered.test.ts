import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let requesterId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `answer-join-request-test-${Date.now()}-${Math.random()}`,
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
  // Input: the organizer answers the same request twice
  // Expected status code: 200 the first time, then 409
  // Expected behavior: the second answer is rejected
  // Expected output: { msg: "already_responded", error: "This request was already answered or withdrawn" }
  //   the second time
  test('Already answered', async () => {
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
