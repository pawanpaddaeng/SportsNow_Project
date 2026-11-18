import * as push from '../../../src/core/notifications/push';
import * as joinRequestsDb from '../../../src/game/db/joinRequests';
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
  testPlayer,
  withToken,
} from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

const REQUEST_ID = 7;

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Mocked: POST /api/games/{gameId}/join-requests', () => {
  // Mocked behavior: createJoinRequest saves nothing and reports a new request.
  //   saveNotifications and sendPushToUser are replaced to check who is told. Sign in is
  //   mocked only so the route can run
  // Input: a valid request for game 42
  // Expected status code: 201
  // Expected behavior: the organizer gets a join_request notification with the game's id,
  //   saved for their Notifications screen and sent as a push
  // Expected output: { msg: "success", request } with the request's id, game_id and status "pending"
  test('Organizer is told', async () => {
    // 1. sign in as the player asking to join (not what this test checks)
    const player = testPlayer();
    const token = signIn(player);

    // 2. a request that saves successfully
    const organizer = testOrganizer();
    const game = testGame();
    jest.spyOn(joinRequestsDb, 'createJoinRequest').mockResolvedValue({
      ok: true,
      requestId: REQUEST_ID,
      organizerId: organizer.userId,
      title: game.title,
    });
    const saveNotifications = jest.spyOn(notificationsDb, 'saveNotifications').mockResolvedValue();
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    // 3. ask to join through the interface, and let the notification finish
    const response = await api.post(`/api/games/${GAME_ID}/join-requests`).set(withToken(token))
      .send({ skill_level: 'beginner' });
    await letNotificationsFinish();

    // 4. check the reply and the notification
    expect(response.status).toBe(201);
    expect(response.body).toEqual({
      msg: 'success',
      request: { request_id: REQUEST_ID, game_id: GAME_ID, status: 'pending' },
    });
    expect(saveNotifications).toHaveBeenCalledWith([organizer.userId], {
      type: 'join_request',
      title: 'New join request',
      body: `${player.firstName} wants to join ${game.title}`,
      gameId: GAME_ID,
    });
    expect(sendPushToUser).toHaveBeenCalledTimes(1);
    expect(sendPushToUser).toHaveBeenCalledWith(organizer.userId, {
      title: 'New join request',
      body: `${player.firstName} wants to join ${game.title}`,
      data: { type: 'join_request', gameId: String(GAME_ID) },
    });
  });

  // Mocked behavior: createJoinRequest throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: a valid request for game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not send your request, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the player asking to join (not what this test checks)
    const token = signIn(testPlayer());

    // 2. make saving fail
    const createJoinRequest = jest.spyOn(joinRequestsDb, 'createJoinRequest')
      .mockRejectedValue(new Error('database down'));

    // 3. ask to join through the interface
    const response = await api.post(`/api/games/${GAME_ID}/join-requests`).set(withToken(token))
      .send({ skill_level: 'beginner' });

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not send your request, try again later' });
    expect(createJoinRequest).toHaveBeenCalledTimes(1);
  });
});
