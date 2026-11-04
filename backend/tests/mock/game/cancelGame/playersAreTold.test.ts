import request from 'supertest';

import { createApp } from '../../../../src/app';
import * as push from '../../../../src/core/notifications/push';
import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testGame, testOrganizer } from '../../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const GAME_ID = 42;

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/cancel
describe('Mocked: POST /api/games/{gameId}/cancel', () => {
  // Mocked behavior: the games database is replaced: cancelGame succeeds, getGameById returns
  //   the cancelled game, and getUsersToNotify returns users 2 and 3. sendPushToUsers is
  //   replaced to check who is told. Sign in is mocked only so the route can run
  // Input: the organizer cancels their game
  // Expected status code: 200
  // Expected behavior: the users to notify get a game_cancelled notification
  // Expected output: { msg: "success", game } with game.status "cancelled"
  test('Players are told', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. a game that cancels successfully
    const game = testGame({ gameId: GAME_ID, organizerId: organizer.userId, status: 'cancelled' });
    jest.spyOn(gamesDb, 'cancelGame').mockResolvedValue({ ok: true });
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(game);
    const getUsersToNotify = jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([2, 3]);
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(2);

    // 3. cancel it through the interface
    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/cancel`)
      .set('Authorization', `Bearer ${token}`);

    // 4. the reply doesn't wait for notifications, let them finish
    await new Promise((resolve) => { setImmediate(resolve); });

    // 5. check the reply and the notification
    expect(response.status).toBe(200);
    expect(response.body.game.status).toBe('cancelled');
    expect(getUsersToNotify).toHaveBeenCalledWith(GAME_ID, organizer.userId);
    expect(sendPushToUsers).toHaveBeenCalledTimes(1);
    expect(sendPushToUsers).toHaveBeenCalledWith([2, 3], {
      title: 'Game cancelled',
      body: `${game.title} at ${game.locationName} was cancelled by the organizer`,
      data: { type: 'game_cancelled', gameId: String(GAME_ID) },
    });
  });
});
