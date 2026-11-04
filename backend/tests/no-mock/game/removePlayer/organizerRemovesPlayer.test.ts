import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool, selectRows } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let playerId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `remove-player-test-organizerRemovesPlayer-${firstName}`,
    email: 'remove-player-test@example.com',
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

// Interface DELETE /api/games/{gameId}/players/{userId}
describe('Unmocked: DELETE /api/games/{gameId}/players/{userId}', () => {
  // Input: the organizer removes the other player from their upcoming game, which has
  //   the organizer and that player in it
  // Expected status code: 200
  // Expected behavior: the player is no longer in the game, only the organizer is left
  // Expected output: { msg: "success", game } with the game's player_count 1
  test('Organizer removes the second player', async () => {
    // 1. an upcoming game with the organizer and a second player in it
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

    // 2. remove the player through the interface
    const response = await request(createApp())
      .delete(`/api/games/${gameId}/players/${playerId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    // 3. check the reply
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: gameId, player_count: 1 });

    // 4. check only the organizer is left in the game
    const players = await selectRows<{ user_id: number }>(
      'SELECT user_id FROM game_players WHERE game_id = ?', [gameId]);
    expect(players).toEqual([{ user_id: organizerId }]);
  });
});
