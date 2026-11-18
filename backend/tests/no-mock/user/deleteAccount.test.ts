import { selectRows } from '../../../src/core/db/mysql';
import * as push from '../../../src/core/notifications/push';
import { findUserById } from '../../../src/user/db/users';
import { addGame, addPlayer, addUser, api, asUser, cleanUpAfterTests, getPlayerIds, withToken } from '../helpers';

let oliviaId: number;
let benId: number;

beforeEach(async () => {
  oliviaId = await addUser('Olivia');
  benId = await addUser('Ben');
});

cleanUpAfterTests();

// whether the game is really still saved
async function gameExists(gameId: number): Promise<boolean> {
  const rows = await selectRows<{ game_id: number }>('SELECT game_id FROM games WHERE game_id = ?', [gameId]);
  return rows.length > 0;
}

// Interface DELETE /api/users/me
describe('Unmocked: DELETE /api/users/me', () => {
  // Input: no Authorization header, then a token that isn't a real JWT
  // Expected status code: 400, then 401
  // Expected behavior: no account is deleted
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    const noToken = await api.delete('/api/users/me');
    expect(noToken.status).toBe(400);
    expect(noToken.body).toMatchObject({ msg: 'invalid_request' });

    const badToken = await api.delete('/api/users/me').set(withToken('not-a-real-token'));
    expect(badToken.status).toBe(401);
    expect(badToken.body).toEqual({ msg: 'invalid_token', error: 'Invalid jwt_token' });

    expect(await findUserById(oliviaId)).not.toBeNull();
  });

  // Input: Olivia organizes an upcoming game Ben plays in, and plays in Ben's upcoming game.
  //   She deletes her account. sendPushToUsers is watched without replacing it, and the test
  //   users have no devices, so no notification leaves the server
  // Expected status code: 200, then 401 when her old token is used again
  // Expected behavior: Olivia's account and the game she organizes are deleted, and she is no
  //   longer a player in Ben's game, which stays. Ben is told her game was cancelled, and that
  //   she left his game. Ben's account stays
  // Expected output: { msg: "success" }, then { msg: "no_user" } for her old token
  test('Deletes the account and the games the user organizes', async () => {
    const oliviasGame = await addGame(oliviaId, { title: 'Olivia Basketball' });
    await addPlayer(oliviasGame, benId);
    const bensGame = await addGame(benId, { title: 'Ben Soccer' });
    await addPlayer(bensGame, oliviaId);
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers');

    const response = await api.delete('/api/users/me').set(asUser(oliviaId));
    // the reply doesn't wait for the notifications, so wait for them before checking
    await Promise.all(sendPushToUsers.mock.results.map((result) => result.value));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });

    expect(await findUserById(oliviaId)).toBeNull();
    expect(await gameExists(oliviasGame)).toBe(false);
    expect(await gameExists(bensGame)).toBe(true);
    expect(await getPlayerIds(bensGame)).toEqual([benId]);
    expect(await findUserById(benId)).not.toBeNull();

    expect(sendPushToUsers).toHaveBeenCalledTimes(2);
    expect(sendPushToUsers).toHaveBeenCalledWith([benId], expect.objectContaining({
      data: { type: 'game_cancelled', gameId: String(oliviasGame) },
    }));
    expect(sendPushToUsers).toHaveBeenCalledWith([benId], expect.objectContaining({
      data: { type: 'player_left', gameId: String(bensGame) },
    }));

    const oldToken = await api.get('/api/users/me').set(asUser(oliviaId));
    expect(oldToken.status).toBe(401);
    expect(oldToken.body).toMatchObject({ msg: 'no_user' });
  });
});
