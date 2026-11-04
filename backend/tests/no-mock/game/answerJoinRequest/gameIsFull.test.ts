import request from 'supertest';

import { createApp } from '../../../../src/app';
import { selectRows } from '../../../../src/core/db/mysql';
import { getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addJoinRequest, addPlayer, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let playerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface PATCH /api/games/{gameId}/join-requests/{requestId}
describe('Unmocked: PATCH /api/games/{gameId}/join-requests/{requestId}', () => {
  // Input: the organizer approves a pending request when no spots are left
  // Expected status code: 409
  // Expected behavior: nothing changes, the request stays pending and the players stay the same
  // Expected output: { msg: "game_full", error: "This game is full, add spots before approving more players" }
  test('Game is full', async () => {
    // 1. a full game for two players, with a pending request
    const gameId = await addGame(organizerId, { maxPlayers: 2 });
    await addPlayer(gameId, playerId);
    const requestId = await addJoinRequest(gameId, requesterId);

    // 2. approve it through the interface
    const response = await request(createApp())
      .patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ decision: 'approve' });

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(409);
    expect(response.body).toEqual({
      msg: 'game_full',
      error: 'This game is full, add spots before approving more players',
    });
    const requests = await selectRows<{ status: string }>(
      'SELECT status FROM join_requests WHERE request_id = ?', [requestId]);
    expect(requests).toEqual([{ status: 'pending' }]);
    expect((await getGameById(gameId))?.playerCount).toBe(2);
  });
});
