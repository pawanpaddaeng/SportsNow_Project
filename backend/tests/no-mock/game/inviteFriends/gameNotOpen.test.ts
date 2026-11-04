import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addPlayer, addUser, cleanUpAfterTests, ONE_DAY_MS } from '../../helpers';

let organizerId: number;
let friendId: number;
let playerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  friendId = await addUser('Fay');
  playerId = await addUser('Paul');
  await executeWrite('INSERT INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)',
    [organizerId, friendId, friendId, organizerId]);
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: the organizer invites their friend to a game that is cancelled, has started,
  //   is completed, or is full
  // Expected status code: 409
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "game_cancelled" }, { msg: "game_started" } or { msg: "game_full" }
  test.each([
    ['cancelled', 'game_cancelled', async () => {
      const gameId = await addGame(organizerId);
      await executeWrite("UPDATE games SET status = 'cancelled' WHERE game_id = ?", [gameId]);
      return gameId;
    }],
    ['started', 'game_started', () => addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) })],
    ['completed', 'game_started', async () => {
      const gameId = await addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) });
      await executeWrite("UPDATE games SET status = 'completed' WHERE game_id = ?", [gameId]);
      return gameId;
    }],
    ['full', 'game_full', async () => {
      const gameId = await addGame(organizerId, { maxPlayers: 2 });
      await addPlayer(gameId, playerId);
      return gameId;
    }],
  ] as const)('Organizer cannot invite to a %s game', async (_name, msg, setUpGame) => {
    const gameId = await setUpGame();

    const response = await request(createApp())
      .post(`/api/games/${gameId}/invites`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ friend_ids: [friendId] });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
  });
});
