import * as mysql from '../../../src/core/db/mysql';
import * as friendsDb from '../../../src/friend/db/friends';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testOrganizer, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface DELETE /api/friends/{userId}
describe('Mocked: DELETE /api/friends/{userId}', () => {
  // Mocked behavior: removeFriend throws, as if the database failed. Sign in is mocked only
  //   so the route can run
  // Input: Olivia removes her friend Paul
  // Expected status code: 500
  // Expected behavior: the error is handled
  // Expected output: { msg: "server_error", error: "Could not remove this friend, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the user removing a friend (not what this test checks)
    const user = testOrganizer();
    const token = signIn(user);
    const friend = testPlayer();

    // 2. make removing the friend fail
    const removeFriend = jest.spyOn(friendsDb, 'removeFriend').mockRejectedValue(new Error('database down'));

    // 3. remove through the interface
    const response = await api.delete(`/api/friends/${friend.userId}`).set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not remove this friend, try again later' });
    expect(removeFriend).toHaveBeenCalledWith(user.userId, friend.userId);
  });

  // Mocked behavior: the database transaction is replaced by a fake connection. Locking the
  //   two users finds both, then deleting the friendship fails, as if the database went down
  //   part way. Sign in is mocked only so the route can run
  // Input: Olivia removes her friend Paul
  // Expected status code: 500
  // Expected behavior: the error is handled and the chat is never deleted
  // Expected output: { msg: "server_error", error: "Could not remove this friend, try again later" }
  test('Database fails while removing', async () => {
    // 1. sign in as the user removing a friend (not what this test checks)
    const user = testOrganizer();
    const token = signIn(user);
    const friend = testPlayer();

    // 2. a transaction that locks both users, then fails to delete
    const execute = jest.fn()
      .mockResolvedValueOnce([[{ user_id: user.userId }, { user_id: friend.userId }], []])
      .mockRejectedValueOnce(new Error('database down'));
    const connection: mysql.Queryable = { execute };
    jest.spyOn(mysql, 'inTransaction')
      .mockImplementation(<T>(work: (transaction: mysql.Queryable) => Promise<T>): Promise<T> => work(connection));

    // 3. remove through the interface
    const response = await api.delete(`/api/friends/${friend.userId}`).set(withToken(token));

    // 4. check the error reply and that only the lock and the failed delete ran
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not remove this friend, try again later' });
    expect(execute).toHaveBeenCalledTimes(2);
  });
});
