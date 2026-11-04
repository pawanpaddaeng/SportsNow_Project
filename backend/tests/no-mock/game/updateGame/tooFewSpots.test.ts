import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame, getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let playerIds: number[];

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `update-game-test-tooFewSpots-${firstName}`,
    email: 'update-game-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerIds = [await addUser('Paul'), await addUser('Pia')];
});

// deleting the users also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await Promise.all(playerIds.map((playerId) => deleteUser(playerId)));
});

afterAll(async () => {
  await pool.end();
});

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: the organizer lowers max_players to 2 when the game has 3 players
  // Expected status code: 400
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "invalid_request", error: "max_players can't be below the 3 players already in the game" }
  test('Too few spots', async () => {
    // 1. a game with 3 players: the organizer and two who joined
    const gameId = await createGame({
      organizerId,
      title: 'Original Title',
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
    await Promise.all(playerIds.map((playerId) => executeWrite(
      'INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, playerId])));

    // 2. lower max_players below that through the interface
    const response = await request(createApp())
      .put(`/api/games/${gameId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({
        title: 'New Title',
        sport: 'basketball',
        starts_at: new Date(Date.now() + ONE_DAY_MS).toISOString(),
        location_name: 'UBC Rec Centre',
        address: '6000 Student Union Blvd',
        latitude: 49.26,
        longitude: -123.25,
        max_players: 2,
        skill_level: 'any',
      });

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: "max_players can't be below the 3 players already in the game",
    });
    const saved = await getGameById(gameId);
    expect(saved?.title).toBe('Original Title');
    expect(saved?.maxPlayers).toBe(10);
  });
});
