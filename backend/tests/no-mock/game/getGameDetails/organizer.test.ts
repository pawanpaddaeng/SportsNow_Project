import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let requesterIds: number[];

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `game-details-test-organizer-${firstName}`,
    email: 'game-details-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterIds = [await addUser('Rae'), await addUser('Rob')];
});

// deleting the users also deletes their games, game players, chats and join requests
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await Promise.all(requesterIds.map((requesterId) => deleteUser(requesterId)));
});

afterAll(async () => {
  await pool.end();
});

// Interface GET /api/games/{gameId}
describe('Unmocked: GET /api/games/{gameId}', () => {
  // Input: the organizer opens their game, which has two pending join requests
  // Expected status code: 200
  // Expected behavior: the organizer sees their role and the waiting requests, nothing changes
  // Expected output: { msg: "success", game, organizer, players, ... } with viewer_role
  //   "organizer" and pending_requests 2
  test('Organizer', async () => {
    // 1. a game with two pending join requests
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
    await Promise.all(requesterIds.map((requesterId) => executeWrite(
      "INSERT INTO join_requests (game_id, user_id, skill_level, status) VALUES (?, ?, 'beginner', 'pending')",
      [gameId, requesterId],
    )));

    // 2. open the game through the interface
    const response = await request(createApp())
      .get(`/api/games/${gameId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    // 3. check the organizer's view
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game.game_id).toBe(gameId);
    expect(response.body.organizer.user_id).toBe(organizerId);
    expect(response.body.viewer_role).toBe('organizer');
    expect(response.body.pending_requests).toBe(2);
  });
});
