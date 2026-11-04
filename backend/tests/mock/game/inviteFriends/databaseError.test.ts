import request from 'supertest';

import { createApp } from '../../../../src/app';
import * as push from '../../../../src/core/notifications/push';
import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testOrganizer } from '../../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const GAME_ID = 42;

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Mocked: POST /api/games/{gameId}/invites', () => {
  // Mocked behavior: getGameById throws as if the database were down.
  //   Push notifications are replaced by a spy
  // Input: the organizer invites friend 5
  // Expected status code: 500
  // Expected behavior: nobody is notified
  // Expected output: { msg: "server_error" }
  test('Database error', async () => {
    const token = signIn(testOrganizer());
    const sendPush = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(0);
    jest.spyOn(gamesDb, 'getGameById').mockRejectedValue(new Error('database is down'));

    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/invites`)
      .set('Authorization', `Bearer ${token}`)
      .send({ friend_ids: [5] });

    expect(response.status).toBe(500);
    expect(response.body.msg).toBe('server_error');
    expect(sendPush).not.toHaveBeenCalled();
  });
});
