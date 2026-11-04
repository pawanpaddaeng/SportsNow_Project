import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let viewerId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `game-details-test-${Date.now()}-${Math.random()}`,
    email: 'game-details-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  viewerId = await addUser('Vera');
});

// deleting the users also deletes their games, game players, chats and join requests
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(viewerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface GET /api/games/{gameId}
describe('Unmocked: GET /api/games/{gameId}', () => {
  // Input: a user opens a game they asked to join
  // Expected status code: 200
  // Expected behavior: the user sees they are only viewing it and their request, nothing changes
  // Expected output: { msg: "success", game, organizer, players, ... } with viewer_role
  //   "viewer" and join_request_status "pending"
  test('Viewer', async () => {
    // 1. a game the user asked to join
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
    await executeWrite(
      "INSERT INTO join_requests (game_id, user_id, skill_level, status) VALUES (?, ?, 'beginner', 'pending')",
      [gameId, viewerId],
    );

    // 2. open the game through the interface
    const response = await request(createApp())
      .get(`/api/games/${gameId}`)
      .set('Authorization', `Bearer ${createAuthToken(viewerId)}`);

    // 3. check the viewer's view
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game.game_id).toBe(gameId);
    expect(response.body.viewer_role).toBe('viewer');
    expect(response.body.join_request_status).toBe('pending');
  });
});
