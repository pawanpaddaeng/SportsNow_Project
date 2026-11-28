import * as push from '../../../src/core/notifications/push';
import * as gamesDb from '../../../src/game/db/games';
import { type ActiveGame } from '../../../src/game/db/games';
import * as usersDb from '../../../src/user/db/users';
import type * as MockEnv from '../mockEnv';
import {
  api,
  type ApiReply,
  cleanUpAfterTests,
  letNotificationsFinish,
  signIn,
  testOrganizer,
  withToken,
} from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// the organizer's own upcoming game
const OWN_GAME = { gameId: 42, organizerId: 1, title: 'Sunday Soccer', locationName: 'Jericho Park' };

/*  replaces everything deleting an account touches: getActiveGamesOfUser returns the given games,
    getUsersToNotify returns users 2 and 3, and deleteUser and sendPushToUsers succeed. A test can
    change one, e.g. make deleteUser throw
 */
function mockAccountDeletion(games: ActiveGame[]) {
  jest.spyOn(gamesDb, 'getActiveGamesOfUser').mockResolvedValue(games);
  return {
    getUsersToNotify: jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([2, 3]),
    deleteUser: jest.spyOn(usersDb, 'deleteUser').mockResolvedValue(),
    sendPushToUsers: jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(1),
  };
}

// deletes the signed in user's account, and lets any notifications finish before the test checks them
async function deleteAccount(token: string): Promise<ApiReply> {
  const response = await api.delete('/api/users/me').set(withToken(token));
  await letNotificationsFinish();
  return response as ApiReply;
}

// Interface DELETE /api/users/me
describe('Mocked: DELETE /api/users/me', () => {
  // Mocked behavior: getActiveGamesOfUser returns the organizer's own game, deleteUser throws
  //   as if the database were down, and sendPushToUsers is watched
  // Input: the organizer deletes their account
  // Expected status code: 500
  // Expected behavior: nobody is told the game was cancelled, since the account wasn't deleted
  // Expected output: { msg: "server_error", error: "Could not delete account, try again later" }
  test('Database fails while deleting the account', async () => {
    const token = signIn(testOrganizer());
    const { deleteUser, sendPushToUsers } = mockAccountDeletion([OWN_GAME]);
    deleteUser.mockRejectedValue(new Error('database is down'));

    const response = await deleteAccount(token);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not delete account, try again later' });
    expect(sendPushToUsers).not.toHaveBeenCalled();
  });
});
