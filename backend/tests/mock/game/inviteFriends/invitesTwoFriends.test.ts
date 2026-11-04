import * as push from '../../../../src/core/notifications/push';
import * as friendsDb from '../../../../src/friend/db/friends';
import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { api, cleanUpAfterTests, GAME_ID, signIn, testGame, testOrganizer, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

// the organizer's friends, neither of them in the game yet
const FRIEND_IDS = [5, 6];

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Mocked: POST /api/games/{gameId}/invites', () => {
  // Mocked behavior: the database is replaced by upcoming game 42 with 3 of 10 players,
  //   and the organizer's friends 5 and 6. Push notifications are replaced by a spy
  // Input: the organizer invites friends 5 and 6
  // Expected status code: 201
  // Expected behavior: friends 5 and 6 each get a "game_invite" notification for game 42
  // Expected output: { msg: "success" }
  test('Organizer invites two friends', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame());
    jest.spyOn(gamesDb, 'getGamePlayers').mockResolvedValue([
      { userId: organizer.userId, firstName: 'Olivia', lastName: 'Organizer', noShow: false },
    ]);
    jest.spyOn(friendsDb, 'areFriends').mockResolvedValue(true);
    const sendPush = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(0);

    const response = await api.post(`/api/games/${GAME_ID}/invites`).set(withToken(token))
      .send({ friend_ids: FRIEND_IDS });

    expect(response.status).toBe(201);
    expect(response.body).toEqual({ msg: 'success' });
    expect(sendPush).toHaveBeenCalledTimes(1);
    expect(sendPush).toHaveBeenCalledWith(FRIEND_IDS, {
      title: 'Game invite',
      body: 'Olivia invited you to Sunday Soccer',
      data: { type: 'game_invite', gameId: String(GAME_ID) },
    });
  });
});
