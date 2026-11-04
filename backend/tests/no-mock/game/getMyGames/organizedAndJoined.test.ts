import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let userId: number;
let otherUserId: number;

// a real user in the database, with a unique google id
function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `get-my-games-test-organizedAndJoined-${firstName}`,
    email: 'get-my-games-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

// a real upcoming game with the user as its organizer and first player
function addGame(organizerId: number, title: string): Promise<number> {
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
  userId = await addUser('Uma');
  otherUserId = await addUser('Omar');
});

// deleting the users also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(userId);
  await deleteUser(otherUserId);
});

afterAll(async () => {
  await pool.end();
});

// Interface GET /api/games/mine
describe('Unmocked: GET /api/games/mine', () => {
  // Input: a signed in user who organizes one game and plays in another user's game
  // Expected status code: 200
  // Expected behavior: each game is listed under the user's role in it, nothing changes
  // Expected output: { msg: "success", organized_games, joined_games } with one game in each
  test('Organized and joined', async () => {
    // 1. one game the user organizes, and one they joined
    const organizedGameId = await addGame(userId, 'My Game');
    const joinedGameId = await addGame(otherUserId, 'Their Game');
    await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [joinedGameId, userId]);

    // 2. get the user's games through the interface
    const response = await request(createApp())
      .get('/api/games/mine')
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    // 3. check each game is in the right list
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.organized_games).toHaveLength(1);
    expect(response.body.organized_games[0]).toMatchObject({
      game_id: organizedGameId,
      organizer_id: userId,
      title: 'My Game',
      player_count: 1,
      pending_requests: 0,
    });
    expect(response.body.joined_games).toHaveLength(1);
    expect(response.body.joined_games[0]).toMatchObject({
      game_id: joinedGameId,
      organizer_id: otherUserId,
      title: 'Their Game',
      player_count: 2,
    });
  });
});
