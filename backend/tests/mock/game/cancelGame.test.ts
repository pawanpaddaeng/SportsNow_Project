import * as logger from '../../../src/core/logger';
import * as push from '../../../src/core/notifications/push';
import * as gamesDb from '../../../src/game/db/games';
import type * as MockEnv from '../mockEnv';
import {
  api,
  cleanUpAfterTests,
  GAME_ID,
  letNotificationsFinish,
  signIn,
  testGame,
  testOrganizer,
  withToken,
} from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

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
    const game = testGame({ status: 'cancelled' });
    jest.spyOn(gamesDb, 'cancelGame').mockResolvedValue({ ok: true });
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(game);
    const getUsersToNotify = jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([2, 3]);
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(2);

    // 3. cancel it through the interface
    const response = await api.post(`/api/games/${GAME_ID}/cancel`).set(withToken(token));

    // 4. let any notifications finish

    await letNotificationsFinish();

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

  // Mocked behavior: cancelGame throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: the organizer cancels game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not cancel game, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. make cancelling fail
    const cancelGame = jest.spyOn(gamesDb, 'cancelGame').mockRejectedValue(new Error('database down'));

    // 3. cancel it through the interface
    const response = await api.post(`/api/games/${GAME_ID}/cancel`).set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not cancel game, try again later' });
    expect(cancelGame).toHaveBeenCalledWith(GAME_ID, organizer.userId);
  });

  // Mocked behavior: cancelGame succeeds, then getGameById finds no game when the route reads
  //   it back, as if it was deleted at the same moment. sendPushToUsers and logError are
  //   watched. Sign in is mocked only so the route can run
  // Input: the organizer cancels game 42
  // Expected status code: 500
  // Expected behavior: the failure is logged and nobody is told
  // Expected output: { msg: "server_error", error: "Could not cancel game, try again later" }
  test('Game is gone right after cancelling', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. a cancel that succeeds, then a game that can't be read back
    jest.spyOn(gamesDb, 'cancelGame').mockResolvedValue({ ok: true });
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(null);
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(0);
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    // 3. cancel it through the interface
    const response = await api.post(`/api/games/${GAME_ID}/cancel`).set(withToken(token));

    // 4. check the error reply, the log, and that nobody was told
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not cancel game, try again later' });
    expect(logError).toHaveBeenCalledWith(`POST /api/games/${GAME_ID}/cancel failed:`, expect.any(Error));
    expect(sendPushToUsers).not.toHaveBeenCalled();
  });

  // Mocked behavior: cancelGame succeeds and getGameById returns the cancelled game, but
  //   getUsersToNotify throws, as if the database failed. logError is watched. Sign in is
  //   mocked only so the route can run
  // Input: the organizer cancels game 42
  // Expected status code: 200, the notifications are best effort
  // Expected behavior: the game is still cancelled and the failed lookup is logged
  // Expected output: { msg: "success", game } with game.status "cancelled"
  test('Looking up who to notify fails', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. a cancel that succeeds, and a lookup of who to tell that fails
    const failure = new Error('database down');
    jest.spyOn(gamesDb, 'cancelGame').mockResolvedValue({ ok: true });
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame({ status: 'cancelled' }));
    jest.spyOn(gamesDb, 'getUsersToNotify').mockRejectedValue(failure);
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    // 3. cancel it through the interface, and let the notifications finish
    const response = await api.post(`/api/games/${GAME_ID}/cancel`).set(withToken(token));
    await letNotificationsFinish();

    // 4. check the reply and that the failure was logged
    expect(response.status).toBe(200);
    expect(response.body.game.status).toBe('cancelled');
    expect(logError).toHaveBeenCalledWith(`Notifying players of game ${GAME_ID} failed:`, failure);
  });
});
