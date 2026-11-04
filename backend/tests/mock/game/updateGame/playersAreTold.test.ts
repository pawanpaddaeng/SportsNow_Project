import * as push from '../../../../src/core/notifications/push';
import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import {
  cleanUpAfterTests,
  GAME_ID,
  letNotificationsFinish,
  signIn,
  testGame,
  testOrganizer,
  validGameBody,
  withToken,
} from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Mocked: PUT /api/games/{gameId}', () => {
  // Mocked behavior: the games database is replaced: getGameById returns the game before and
  //   after the edit, updateGame reports a change, and getUsersToNotify returns users 2 and 3.
  //   sendPushToUsers is replaced to check who is told. Sign in is mocked only so the route can run
  // Input: the organizer changes their game's title
  // Expected status code: 200
  // Expected behavior: the users to notify get a game_updated notification saying what changed
  // Expected output: { msg: "success", game } with the new title
  test('Players are told', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. a game whose title changes from the edit
    const before = testGame({ title: 'Old Title' });
    const after = testGame({ title: 'New Title' });
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValueOnce(before).mockResolvedValueOnce(after);
    jest.spyOn(gamesDb, 'updateGame').mockResolvedValue({ ok: true, changed: true });
    const getUsersToNotify = jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([2, 3]);
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(2);

    // 3. edit it through the interface
    const response = await withToken(token).put(`/api/games/${GAME_ID}`)
      .send(validGameBody());

    // 4. let any notifications finish

    await letNotificationsFinish();

    // 5. check the reply and the notification
    expect(response.status).toBe(200);
    expect(response.body.game.title).toBe('New Title');
    expect(getUsersToNotify).toHaveBeenCalledWith(GAME_ID, organizer.userId);
    expect(sendPushToUsers).toHaveBeenCalledTimes(1);
    expect(sendPushToUsers).toHaveBeenCalledWith([2, 3], {
      title: 'Game updated',
      body: 'New Title: the organizer changed the title',
      data: { type: 'game_updated', gameId: String(GAME_ID) },
    });
  });
});
