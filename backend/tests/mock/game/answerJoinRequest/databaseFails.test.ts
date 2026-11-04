import request from 'supertest';

import { createApp } from '../../../../src/app';
import * as joinRequestsDb from '../../../../src/game/db/joinRequests';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testOrganizer } from '../../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const GAME_ID = 42;
const REQUEST_ID = 7;

cleanUpAfterTests();

// Interface PATCH /api/games/{gameId}/join-requests/{requestId}
describe('Mocked: PATCH /api/games/{gameId}/join-requests/{requestId}', () => {
  // Mocked behavior: decideJoinRequest throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: the organizer approves request 7 for game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not save your answer, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. make saving the answer fail
    const decideJoinRequest = jest.spyOn(joinRequestsDb, 'decideJoinRequest')
      .mockRejectedValue(new Error('database down'));

    // 3. approve it through the interface
    const response = await request(createApp())
      .patch(`/api/games/${GAME_ID}/join-requests/${REQUEST_ID}`)
      .set('Authorization', `Bearer ${token}`)
      .send({ decision: 'approve' });

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not save your answer, try again later' });
    expect(decideJoinRequest).toHaveBeenCalledWith(GAME_ID, REQUEST_ID, organizer.userId, true);
  });
});
