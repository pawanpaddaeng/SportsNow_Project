import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool, selectRows } from '../../../../src/core/db/mysql';
import { createGame, getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let playerId: number;
let otherPlayerId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `leave-game-test-${Date.now()}-${Math.random()}`,
    email: 'leave-game-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  otherPlayerId = await addUser('Pia');
});

// deleting the users also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(playerId);
  await deleteUser(otherPlayerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/leave
describe('Unmocked: POST /api/games/{gameId}/leave', () => {
  // Input: a player leaves an upcoming game, which also has another player in it
  // Expected status code: 200
  // Expected behavior: the player is no longer in the game; the other player still is,
  //   and the game still exists
  // Expected output: { msg: "success", game, game_deleted: false } with the game's
  //   player_count down by one
  test('Player leaves a game that has another player', async () => {
    // 1. an upcoming game with the organizer and two players
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
    await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, otherPlayerId]);
    expect((await getGameById(gameId))?.playerCount).toBe(3);

    // 2. leave it through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/leave`)
      .set('Authorization', `Bearer ${createAuthToken(playerId)}`);

    // 3. check the reply
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game_deleted).toBe(false);
    expect(response.body.game).toMatchObject({ game_id: gameId, player_count: 2 });

    // 4. check only that player left, and the game still exists
    expect(await getGameById(gameId)).not.toBeNull();
    const players = await selectRows<{ user_id: number }>(
      'SELECT user_id FROM game_players WHERE game_id = ?', [gameId]);
    const playerIds = players.map((player) => player.user_id);
    expect(playerIds).toHaveLength(2);
    expect(playerIds).toEqual(expect.arrayContaining([organizerId, otherPlayerId]));
  });
});
