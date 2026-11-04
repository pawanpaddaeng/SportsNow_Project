import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool, selectRows } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let requesterId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `send-join-request-test-validRequest-${firstName}`,
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
  // Input: skill_level, play_frequency and message for another user's game
  // Expected status code: 201
  // Expected behavior: a pending request is saved with the answers
  // Expected output: { msg: "success", request } with the request's id, game_id and status "pending"
  test('Valid request', async () => {
    // 1. another user's upcoming game
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

    // 2. ask to join through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(requesterId)}`)
      .send({ skill_level: 'intermediate', play_frequency: 'weekly', message: 'Happy to play any position' });

    // 3. check the reply
    expect(response.status).toBe(201);
    expect(response.body.msg).toBe('success');
    expect(response.body.request).toMatchObject({ game_id: gameId, status: 'pending' });

    // 4. check what was really saved
    const rows = await selectRows<Record<string, unknown>>(
      `SELECT request_id, skill_level, play_frequency, message, status
       FROM join_requests WHERE game_id = ? AND user_id = ?`,
      [gameId, requesterId],
    );
    expect(rows).toEqual([{
      request_id: response.body.request.request_id,
      skill_level: 'intermediate',
      play_frequency: 'weekly',
      message: 'Happy to play any position',
      status: 'pending',
    }]);
  });
});
