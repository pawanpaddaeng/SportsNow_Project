import request from 'supertest';

import { createApp } from '../../../../src/app';
import { selectRows } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addPlayer, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let playerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
});

cleanUpAfterTests();

// Interface DELETE /api/games/{gameId}/players/{userId}
describe('Unmocked: DELETE /api/games/{gameId}/players/{userId}', () => {
  // Input: the organizer removes the other player from their upcoming game, which has
  //   the organizer and that player in it
  // Expected status code: 200
  // Expected behavior: the player is no longer in the game, only the organizer is left
  // Expected output: { msg: "success", game } with the game's player_count 1
  test('Organizer removes the second player', async () => {
    // 1. an upcoming game with the organizer and a second player in it
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);

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
