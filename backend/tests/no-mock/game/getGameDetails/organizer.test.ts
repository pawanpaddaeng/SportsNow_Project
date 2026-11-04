import request from 'supertest';

import { createApp } from '../../../../src/app';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addJoinRequest, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let requesterIds: number[];

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterIds = [await addUser('Rae'), await addUser('Rob')];
});

cleanUpAfterTests();

// Interface GET /api/games/{gameId}
describe('Unmocked: GET /api/games/{gameId}', () => {
  // Input: the organizer opens their game, which has two pending join requests
  // Expected status code: 200
  // Expected behavior: the organizer sees their role and the waiting requests, nothing changes
  // Expected output: { msg: "success", game, organizer, players, ... } with viewer_role
  //   "organizer" and pending_requests 2
  test('Organizer', async () => {
    // 1. a game with two pending join requests
    const gameId = await addGame(organizerId);
    await Promise.all(requesterIds.map((requesterId) => addJoinRequest(gameId, requesterId)));

    // 2. open the game through the interface
    const response = await request(createApp())
      .get(`/api/games/${gameId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    // 3. check the organizer's view
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game.game_id).toBe(gameId);
    expect(response.body.organizer.user_id).toBe(organizerId);
    expect(response.body.viewer_role).toBe('organizer');
    expect(response.body.pending_requests).toBe(2);
  });
});
