import * as push from '../../../src/core/notifications/push';
import * as friendsDb from '../../../src/friend/db/friends';
import * as gamesDb from '../../../src/game/db/games';
import * as notificationsDb from '../../../src/notification/db/notifications';
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

// the organizer's friends, neither of them in the game yet
const FRIEND_IDS = [5, 6];

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Mocked: POST /api/games/{gameId}/invites', () => {
  // Mocked behavior: the database is replaced by upcoming game 42 with 3 of 10 players,
  //   and the organizer's friends 5 and 6. Saving notifications and push notifications
  //   are replaced by spies
  // Input: the organizer invites friends 5 and 6
  // Expected status code: 201
  // Expected behavior: friends 5 and 6 each get a "game_invite" notification for game 42,
  //   saved for their Notifications screen and sent as a push
  // Expected output: { msg: "success" }
  test('Organizer invites two friends', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame());
    jest.spyOn(gamesDb, 'getGamePlayers').mockResolvedValue([
      { userId: organizer.userId, firstName: 'Olivia', lastName: 'Organizer', noShow: false },
    ]);
    jest.spyOn(friendsDb, 'areFriends').mockResolvedValue(true);
    const saveNotifications = jest.spyOn(notificationsDb, 'saveNotifications').mockResolvedValue();
    const sendPush = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(0);

    const response = await api.post(`/api/games/${GAME_ID}/invites`).set(withToken(token))
      .send({ friend_ids: FRIEND_IDS });
    await letNotificationsFinish();

    expect(response.status).toBe(201);
    expect(response.body).toEqual({ msg: 'success' });
    expect(saveNotifications).toHaveBeenCalledWith(FRIEND_IDS, {
      type: 'game_invite',
      title: 'Game invite',
      body: 'Olivia invited you to Sunday Soccer',
      gameId: GAME_ID,
    });
    expect(sendPush).toHaveBeenCalledTimes(FRIEND_IDS.length);
    for (const friendId of FRIEND_IDS) {
      expect(sendPush).toHaveBeenCalledWith(friendId, {
        title: 'Game invite',
        body: 'Olivia invited you to Sunday Soccer',
        data: { type: 'game_invite', gameId: String(GAME_ID) },
      });
    }
  });

  // Mocked behavior: getGameById throws as if the database were down.
  //   Push notifications are replaced by a spy
  // Input: the organizer invites friend 5
  // Expected status code: 500
  // Expected behavior: nobody is notified
  // Expected output: { msg: "server_error" }
  test('Database error', async () => {
    const token = signIn(testOrganizer());
    const sendPush = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(0);
    jest.spyOn(gamesDb, 'getGameById').mockRejectedValue(new Error('database is down'));

    const response = await api.post(`/api/games/${GAME_ID}/invites`).set(withToken(token))
      .send({ friend_ids: [5] });

    expect(response.status).toBe(500);
    expect(response.body.msg).toBe('server_error');
    expect(sendPush).not.toHaveBeenCalled();
  });
});
