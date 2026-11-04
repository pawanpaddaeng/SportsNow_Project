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
    googleId: `send-join-request-test-askAgain-${firstName}`,
    email: 'send-join-request-test@example.com',
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

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: new answers from a user whose earlier request for the game was declined
  // Expected status code: 201
  // Expected behavior: the same request goes back to pending with the new answers, no second one is added
  // Expected output: { msg: "success", request } with the same request id and status "pending"
  test('Ask again', async () => {
    // 1. a game the user's earlier request was declined for
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
    const declined = await executeWrite(
      `INSERT INTO join_requests (game_id, user_id, skill_level, play_frequency, message, status)
       VALUES (?, ?, 'beginner', 'rarely', 'First try', 'declined')`,
      [gameId, requesterId],
    );

    // 2. ask again with new answers through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(requesterId)}`)
      .send({ skill_level: 'advanced', play_frequency: 'weekly', message: 'Second try' });

    // 3. check the reply
    expect(response.status).toBe(201);
    expect(response.body.request).toMatchObject({ request_id: declined.insertId, status: 'pending' });

    // 4. check there's still one request, pending with the new answers
    const rows = await selectRows<Record<string, unknown>>(
      `SELECT request_id, skill_level, play_frequency, message, status
       FROM join_requests WHERE game_id = ? AND user_id = ?`,
      [gameId, requesterId],
    );
    expect(rows).toEqual([{
      request_id: declined.insertId,
      skill_level: 'advanced',
      play_frequency: 'weekly',
      message: 'Second try',
      status: 'pending',
    }]);
  });
});
