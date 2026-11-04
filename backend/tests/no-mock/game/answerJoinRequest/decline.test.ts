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
  // Input: the organizer sends { decision: "decline" } for a pending request
  // Expected status code: 200
  // Expected behavior: the request is declined, the players don't change
  // Expected output: { msg: "success", request, game } with status "declined" and player_count still 1
  test('Decline', async () => {
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

    // 2. decline it through the interface
    const response = await request(createApp())
      .patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ decision: 'decline' });

    // 3. check the reply
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.request).toEqual({ request_id: requestId, status: 'declined' });
    expect(response.body.game).toMatchObject({ game_id: gameId, player_count: 1 });

    // 4. check what was really saved
    const requests = await selectRows<{ status: string }>(
      'SELECT status FROM join_requests WHERE request_id = ?', [requestId]);
    expect(requests).toEqual([{ status: 'declined' }]);
    const players = await selectRows<{ user_id: number }>(
      'SELECT user_id FROM game_players WHERE game_id = ?', [gameId]);
    expect(players).toEqual([{ user_id: organizerId }]);
  });
});
