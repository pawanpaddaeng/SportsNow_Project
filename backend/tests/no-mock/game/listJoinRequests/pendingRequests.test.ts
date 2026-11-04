import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
// a new player's score: every game counts as perfect until they have played some
const NEW_PLAYER_SCORE = 5;

let organizerId: number;
let olderRequesterId: number;
let newerRequesterId: number;
let declinedRequesterId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `list-join-requests-test-pendingRequests-${firstName}`,
    email: 'list-join-requests-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  olderRequesterId = await addUser('Otto');
  newerRequesterId = await addUser('Nina');
  declinedRequesterId = await addUser('Dan');
});

// deleting the users also deletes their games, game players, chats and join requests
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(olderRequesterId);
  await deleteUser(newerRequesterId);
  await deleteUser(declinedRequesterId);
});

afterAll(async () => {
  await pool.end();
});

// Interface GET /api/games/{gameId}/join-requests
describe('Unmocked: GET /api/games/{gameId}/join-requests', () => {
  // Input: the organizer opens the list for their game, which has two pending requests and one declined
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: { msg: "success", game, requests } with only the two pending requests,
  //   newest first, each with the person's name, answers and reputation_score
  test('Pending requests', async () => {
    // 1. a game with two pending requests and one declined. The newer one is added
    //    first, so the order can't come from when the rows were added
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
      `INSERT INTO join_requests (game_id, user_id, skill_level, play_frequency, message, status, created_at)
       VALUES (?, ?, 'advanced', 'weekly', 'Newer request', 'pending', '2026-01-02 00:00:00')`,
      [gameId, newerRequesterId],
    );
    await executeWrite(
      `INSERT INTO join_requests (game_id, user_id, skill_level, status, created_at)
       VALUES (?, ?, 'beginner', 'pending', '2026-01-01 00:00:00')`,
      [gameId, olderRequesterId],
    );
    await executeWrite(
      `INSERT INTO join_requests (game_id, user_id, skill_level, status, created_at)
       VALUES (?, ?, 'beginner', 'declined', '2026-01-03 00:00:00')`,
      [gameId, declinedRequesterId],
    );

    // 2. open the list through the interface
    const response = await request(createApp())
      .get(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    // 3. check only the pending requests are listed, newest first
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game.game_id).toBe(gameId);
    expect(response.body.requests).toHaveLength(2);
    expect(response.body.requests[0]).toMatchObject({
      user: { user_id: newerRequesterId, first_name: 'Nina', last_name: 'Tester' },
      skill_level: 'advanced',
      play_frequency: 'weekly',
      message: 'Newer request',
      reputation_score: NEW_PLAYER_SCORE,
    });
    expect(response.body.requests[1]).toMatchObject({
      user: { user_id: olderRequesterId, first_name: 'Otto', last_name: 'Tester' },
      skill_level: 'beginner',
      play_frequency: null,
      message: null,
      reputation_score: NEW_PLAYER_SCORE,
    });
  });
});
