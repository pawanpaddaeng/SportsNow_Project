import request from 'supertest';

import { createApp } from '../../../../src/app';
import { selectRows } from '../../../../src/core/db/mysql';
import { getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addPlayer, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let playerId: number;
let otherPlayerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  otherPlayerId = await addUser('Pia');
});

cleanUpAfterTests();

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
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);
    await addPlayer(gameId, otherPlayerId);
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
