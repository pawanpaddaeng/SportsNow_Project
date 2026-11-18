import * as push from '../../../src/core/notifications/push';
import * as gamesDb from '../../../src/game/db/games';
import * as usersDb from '../../../src/user/db/users';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, letNotificationsFinish, signIn, testOrganizer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// the organizer's own upcoming game, and an upcoming game of user 2 they joined
const OWN_GAME = { gameId: 42, organizerId: 1, title: 'Sunday Soccer', locationName: 'Jericho Park' };
const JOINED_GAME = { gameId: 43, organizerId: 2, title: 'Friday Hoops', locationName: 'UBC Rec Centre' };

// Interface DELETE /api/users/me
describe('Mocked: DELETE /api/users/me', () => {
  // Mocked behavior: getActiveGamesOfUser returns the organizer's own game and a game of user 2
  //   they joined. getUsersToNotify returns users 2 and 3 for their own game. deleteUser is
  //   replaced, and sendPushToUsers is replaced to check who is told
  // Input: the organizer deletes their account
  // Expected status code: 200
  // Expected behavior: deleteUser gets the organizer's id. Users 2 and 3 are told the organizer's
  //   game was cancelled, and user 2 is told the organizer left their game
  // Expected output: { msg: "success" }
  test('Players of the user\'s games are told', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    jest.spyOn(gamesDb, 'getActiveGamesOfUser').mockResolvedValue([OWN_GAME, JOINED_GAME]);
    const getUsersToNotify = jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([2, 3]);
    const deleteUser = jest.spyOn(usersDb, 'deleteUser').mockResolvedValue();
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(1);

    const response = await api.delete('/api/users/me').set(withToken(token));
    await letNotificationsFinish();

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(deleteUser).toHaveBeenCalledWith(organizer.userId);
    expect(getUsersToNotify).toHaveBeenCalledWith(OWN_GAME.gameId, organizer.userId);
    expect(sendPushToUsers).toHaveBeenCalledTimes(2);
    expect(sendPushToUsers).toHaveBeenCalledWith([2, 3], {
      title: 'Game cancelled',
      body: 'Sunday Soccer at Jericho Park was cancelled, the organizer deleted their account',
      data: { type: 'game_cancelled', gameId: '42' },
    });
    expect(sendPushToUsers).toHaveBeenCalledWith([2], {
      title: 'Player left',
      body: 'Olivia left Friday Hoops',
      data: { type: 'player_left', gameId: '43' },
    });
  });

  // Mocked behavior: getActiveGamesOfUser returns the organizer's own game, deleteUser throws
  //   as if the database were down, and sendPushToUsers is watched
  // Input: the organizer deletes their account
  // Expected status code: 500
  // Expected behavior: nobody is told the game was cancelled, since the account wasn't deleted
  // Expected output: { msg: "server_error", error: "Could not delete account, try again later" }
  test('Database fails while deleting the account', async () => {
    const token = signIn(testOrganizer());
    jest.spyOn(gamesDb, 'getActiveGamesOfUser').mockResolvedValue([OWN_GAME]);
    jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([2, 3]);
    jest.spyOn(usersDb, 'deleteUser').mockRejectedValue(new Error('database is down'));
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(1);

    const response = await api.delete('/api/users/me').set(withToken(token));
    await letNotificationsFinish();

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not delete account, try again later' });
    expect(sendPushToUsers).not.toHaveBeenCalled();
  });
});
