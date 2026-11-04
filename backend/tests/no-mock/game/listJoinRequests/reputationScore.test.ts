import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
// past games the requester played. game_attendance and player_ratings don't reference
// the games table, so these needn't be real games
const NO_SHOW_GAME_ID = 900001;
const RATED_GAME_ID = 900002;
// (no-show 1 + rated 3 + three games still counted as perfect 5 + 5 + 5) / 5 games
const EXPECTED_SCORE = 3.8;

let organizerId: number;
let requesterId: number;
let raterId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `list-join-requests-test-${Date.now()}-${Math.random()}`,
    email: 'list-join-requests-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
  raterId = await addUser('Remy');
});

// deleting the users also deletes their games, join requests, attendance and ratings
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(requesterId);
  await deleteUser(raterId);
});

afterAll(async () => {
  await pool.end();
});

// Interface GET /api/games/{gameId}/join-requests
describe('Unmocked: GET /api/games/{gameId}/join-requests', () => {
  // Input: the organizer opens the list, where the requester has a no-show in one past game
  //   and was rated 3 stars in every category by the only other player in another
  // Expected status code: 200
  // Expected behavior: the score is worked out from the requester's real history
  // Expected output: { msg: "success", requests } with the request's reputation_score 3.8
  test('Reputation score', async () => {
    // 1. a game the requester asked to join
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
      [gameId, requesterId],
    );

    // 2. the requester's history: a no-show in one game, 3 stars from the other player in another
    await executeWrite(
      `INSERT INTO game_attendance (game_id, user_id, no_show, played_at)
       VALUES (?, ?, TRUE, '2026-01-01 00:00:00'),
              (?, ?, FALSE, '2026-01-02 00:00:00'),
              (?, ?, FALSE, '2026-01-02 00:00:00')`,
      [NO_SHOW_GAME_ID, requesterId, RATED_GAME_ID, requesterId, RATED_GAME_ID, raterId],
    );
    await executeWrite(
      `INSERT INTO player_ratings (game_id, rater_id, rated_id, punctuality, sportsmanship, skill, reliability, overall)
       VALUES (?, ?, ?, 3, 3, 3, 3, 3)`,
      [RATED_GAME_ID, raterId, requesterId],
    );

    // 3. open the list through the interface
    const response = await request(createApp())
      .get(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    // 4. check the score
    expect(response.status).toBe(200);
    expect(response.body.requests).toHaveLength(1);
    expect(response.body.requests[0].user.user_id).toBe(requesterId);
    expect(response.body.requests[0].reputation_score).toBe(EXPECTED_SCORE);
  });
});
