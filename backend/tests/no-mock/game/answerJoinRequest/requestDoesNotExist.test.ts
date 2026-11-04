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
    googleId: `answer-join-request-test-requestDoesNotExist-${firstName}`,
    email: 'answer-join-request-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

// a real upcoming game of the organizer's
function addGame(title: string): Promise<number> {
  return createGame({
    organizerId,
    title,
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
  // Input: the organizer answers one game's id with a request id from their other game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "request_not_found", error: "This join request does not exist" }
  test("Request doesn't exist", async () => {
    // 1. two games, and a request for the second one
    const gameId = await addGame('First Game');
    const otherGameId = await addGame('Second Game');
    const { insertId: otherRequestId } = await executeWrite(
      "INSERT INTO join_requests (game_id, user_id, skill_level, status) VALUES (?, ?, 'beginner', 'pending')",
      [otherGameId, requesterId],
    );

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
