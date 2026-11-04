import request from 'supertest';

import { createApp } from '../../../../src/app';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addPlayer, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let playerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
});

cleanUpAfterTests();

// Interface GET /api/games/{gameId}
describe('Unmocked: GET /api/games/{gameId}', () => {
  // Input: a user opens a game they joined
  // Expected status code: 200
  // Expected behavior: the user sees their role in the game, nothing changes
  // Expected output: { msg: "success", game, organizer, players, ... } with viewer_role "player"
  test('Player', async () => {
    // 1. a game the user joined
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);

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
