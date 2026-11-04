import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
// test files run at the same time on one database and Discover sees everyone's games,
// so each Discover test searches its own remote spot
const SEARCH_LATITUDE = 30;
const SEARCH_LONGITUDE = -150;
const MAX_PLAYERS = 2;

let userId: number;
let organizerId: number;
let playerId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `discover-games-test-fullGameStillShows-${firstName}`,
    email: 'discover-games-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  userId = await addUser('Uma');
  organizerId = await addUser('Omar');
  playerId = await addUser('Paul');
});

// deleting the users also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(userId);
  await deleteUser(organizerId);
  await deleteUser(playerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface GET /api/games/discover
describe('Unmocked: GET /api/games/discover', () => {
  // Input: a signed in user's location, with a nearby game that has no spots left
  // Expected status code: 200
  // Expected behavior: the full game is still listed so the app can show it as full
  // Expected output: { msg: "success", games } with the game, player_count equal to max_players
  test('Full game still shows', async () => {
    // 1. a game for two players: its organizer and one who joined
    const gameId = await createGame({
      organizerId,
      title: 'Full Game',
      sport: 'basketball',
      description: null,
      startsAt: new Date(Date.now() + ONE_DAY_MS),
      endsAt: null,
      locationName: 'Test Field',
      address: '1 Test Rd',
      latitude: SEARCH_LATITUDE,
      longitude: SEARCH_LONGITUDE,
      maxPlayers: MAX_PLAYERS,
      skillLevel: 'any',
    });
    await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, playerId]);

    // 2. discover games through the interface
    const response = await request(createApp())
      .get('/api/games/discover')
      .query({ lat: SEARCH_LATITUDE, lng: SEARCH_LONGITUDE })
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    // 3. check the full game is listed
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.games).toHaveLength(1);
    expect(response.body.games[0].game).toMatchObject({
      game_id: gameId,
      max_players: MAX_PLAYERS,
      player_count: MAX_PLAYERS,
    });
  });
});
