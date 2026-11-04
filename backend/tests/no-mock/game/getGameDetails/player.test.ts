import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let playerId: number;

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
  playerId = await addUser('Paul');
});

// deleting the users also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(playerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface GET /api/games/{gameId}
describe('Unmocked: GET /api/games/{gameId}', () => {
  // Input: a user opens a game they joined
  // Expected status code: 200
  // Expected behavior: the user sees their role in the game, nothing changes
  // Expected output: { msg: "success", game, organizer, players, ... } with viewer_role "player"
  test('Player', async () => {
    // 1. a game the user joined
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
    await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, playerId]);

    // 2. open the game through the interface
    const response = await request(createApp())
      .get(`/api/games/${gameId}`)
      .set('Authorization', `Bearer ${createAuthToken(playerId)}`);

    // 3. check the player's view
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game.game_id).toBe(gameId);
    expect(response.body.viewer_role).toBe('player');
  });
});
