import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { api, cleanUpAfterTests, GAME_ID, signIn, testGame, testOrganizer, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/complete
describe('Mocked: POST /api/games/{gameId}/complete', () => {
  // Mocked behavior: completeGame is replaced so only the route's handling of the body runs.
  //   Sign in and notifications are mocked only so the route can run
  // Input: the organizer completes game 42 with players 7 and 8 unticked, 7 sent twice
  // Expected status code: 200
  // Expected behavior: completeGame gets each no-show once
  // Expected output: { msg: "success", game }
  test('Organizer marks players who did not show up', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([]);
    const game = testGame({ hasStarted: true, status: 'completed' });
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(game);
    const completeGame = jest.spyOn(gamesDb, 'completeGame').mockResolvedValue({ ok: true });

    const response = await api.post(`/api/games/${GAME_ID}/complete`).set(withToken(token))
      .send({ no_show_user_ids: [7, 8, 7] });

    expect(response.status).toBe(200);
    expect(completeGame).toHaveBeenCalledWith(GAME_ID, organizer.userId, [7, 8]);
  });
});
