import request from 'supertest';

import { createApp } from '../../../../src/app';
import * as joinRequestsDb from '../../../../src/game/db/joinRequests';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testPlayer } from '../../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const GAME_ID = 42;

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Mocked: POST /api/games/{gameId}/join-requests', () => {
  // Mocked behavior: createJoinRequest throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: a valid request for game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not send your request, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the player asking to join (not what this test checks)
    const token = signIn(testPlayer());

    // 2. make saving fail
    const createJoinRequest = jest.spyOn(joinRequestsDb, 'createJoinRequest')
      .mockRejectedValue(new Error('database down'));

    // 3. ask to join through the interface
    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/join-requests`)
      .set('Authorization', `Bearer ${token}`)
      .send({ skill_level: 'beginner' });

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not send your request, try again later' });
    expect(createJoinRequest).toHaveBeenCalledTimes(1);
  });
});
