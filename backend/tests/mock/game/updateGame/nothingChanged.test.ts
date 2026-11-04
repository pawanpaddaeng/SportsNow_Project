import request from 'supertest';

import { createApp } from '../../../../src/app';
import * as push from '../../../../src/core/notifications/push';
import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testGame, testOrganizer } from '../../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const GAME_ID = 42;

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Mocked: PUT /api/games/{gameId}', () => {
  // Mocked behavior: the games database is replaced: getGameById returns the same game before
  //   and after, and updateGame reports no change. sendPushToUsers is replaced to check it
  //   isn't called. Sign in is mocked only so the route can run
  // Input: the organizer saves their game with the same values
  // Expected status code: 200
  // Expected behavior: no notification is sent
  // Expected output: { msg: "success", game }
  test('Nothing changed', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. a game the edit doesn't change
    const game = testGame({ gameId: GAME_ID, organizerId: organizer.userId });
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(game);
    jest.spyOn(gamesDb, 'updateGame').mockResolvedValue({ ok: true, changed: false });
    jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([2, 3]);
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(2);

    // 3. save the same values through the interface
    const response = await request(createApp())
      .put(`/api/games/${GAME_ID}`)
      .set('Authorization', `Bearer ${token}`)
      .send({
        title: game.title,
        sport: game.sport,
        starts_at: new Date(Date.now() + ONE_DAY_MS).toISOString(),
        location_name: game.locationName,
        address: game.address,
        latitude: game.latitude,
        longitude: game.longitude,
        max_players: game.maxPlayers,
        skill_level: game.skillLevel,
      });

    // 4. the reply doesn't wait for notifications, let any finish
    await new Promise((resolve) => { setImmediate(resolve); });

    // 5. check the reply, and that nobody was told
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(sendPushToUsers).not.toHaveBeenCalled();
  });
});
