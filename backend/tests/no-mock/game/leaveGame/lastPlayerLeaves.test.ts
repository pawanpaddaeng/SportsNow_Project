import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, selectRows } from '../../../../src/core/db/mysql';
import { getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/leave
describe('Unmocked: POST /api/games/{gameId}/leave', () => {
  // Input: the organizer, the only player left in their cancelled game, leaves it
  //   (the organizer is always a player, and can only leave once the game is cancelled or completed)
  // Expected status code: 200
  // Expected behavior: with no players left the game is deleted, with its players and chat
  // Expected output: { msg: "success", game: null, game_deleted: true }
  test('Last player leaves and the game is deleted', async () => {
    // 1. a cancelled game whose only player is the organizer
    const gameId = await addGame(organizerId);
    await executeWrite("UPDATE games SET status = 'cancelled' WHERE game_id = ?", [gameId]);

    // 2. leave it through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/leave`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    // 3. check the reply
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', game: null, game_deleted: true });

    // 4. check the game, its players and its chat are really gone
    expect(await getGameById(gameId)).toBeNull();
    const players = await selectRows<{ user_id: number }>(
      'SELECT user_id FROM game_players WHERE game_id = ?', [gameId]);
    expect(players).toEqual([]);
    const chats = await selectRows<{ chat_id: number }>(
      'SELECT chat_id FROM chats WHERE game_id = ?', [gameId]);
    expect(chats).toEqual([]);
  });
});
