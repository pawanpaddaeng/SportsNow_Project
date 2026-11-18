import * as push from '../../../src/core/notifications/push';
import * as gamesDb from '../../../src/game/db/games';
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
  withToken,
} from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

const REQUEST_ID = 7;
const APPROVED_REQUEST_ID = 7;
const DECLINED_REQUEST_ID = 8;
const APPROVED_USER_ID = 2;
const DECLINED_USER_ID = 3;

cleanUpAfterTests();

// Interface PATCH /api/games/{gameId}/join-requests/{requestId}
describe('Mocked: PATCH /api/games/{gameId}/join-requests/{requestId}', () => {
  // Mocked behavior: decideJoinRequest saves nothing and reports whose request was answered,
  //   and getGameById returns the game. saveNotifications and sendPushToUser are replaced to
  //   check who is told. Sign in is mocked only so the route can run
  // Input: the organizer approves one request and declines another
  // Expected status code: 200 for both
  // Expected behavior: the first person gets join_request_approved and the second gets
  //   join_request_declined, each saved for their Notifications screen and sent as a push
  // Expected output: { msg: "success", request, game } with each request's new status
  test('Person is told', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. two requests that save successfully
    const game = testGame();
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(game);
    jest.spyOn(joinRequestsDb, 'decideJoinRequest')
      .mockResolvedValueOnce({ ok: true, requesterId: APPROVED_USER_ID, title: game.title })
      .mockResolvedValueOnce({ ok: true, requesterId: DECLINED_USER_ID, title: game.title });
    const saveNotifications = jest.spyOn(notificationsDb, 'saveNotifications').mockResolvedValue();
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    // 3. approve the first through the interface
    const approved = await api.patch(`/api/games/${GAME_ID}/join-requests/${APPROVED_REQUEST_ID}`)
      .set(withToken(token))
      .send({ decision: 'approve' });

    // 4. decline the second through the interface
    const declined = await api.patch(`/api/games/${GAME_ID}/join-requests/${DECLINED_REQUEST_ID}`)
      .set(withToken(token))
      .send({ decision: 'decline' });
    await letNotificationsFinish();

    // 5. check the replies and who was told what
    expect(approved.status).toBe(200);
    expect(approved.body.request).toEqual({ request_id: APPROVED_REQUEST_ID, status: 'approved' });
    expect(declined.status).toBe(200);
    expect(declined.body.request).toEqual({ request_id: DECLINED_REQUEST_ID, status: 'declined' });
    expect(saveNotifications).toHaveBeenCalledWith([APPROVED_USER_ID], {
      type: 'join_request_approved',
      title: 'Request approved',
      body: `You're in! You joined ${game.title}`,
      gameId: GAME_ID,
    });
    expect(saveNotifications).toHaveBeenCalledWith([DECLINED_USER_ID], {
      type: 'join_request_declined',
      title: 'Request declined',
      body: `The organizer declined your request to join ${game.title}`,
      gameId: GAME_ID,
    });
    expect(sendPushToUser).toHaveBeenCalledTimes(2);
    expect(sendPushToUser).toHaveBeenCalledWith(APPROVED_USER_ID, {
      title: 'Request approved',
      body: `You're in! You joined ${game.title}`,
      data: { type: 'join_request_approved', gameId: String(GAME_ID) },
    });
    expect(sendPushToUser).toHaveBeenCalledWith(DECLINED_USER_ID, {
      title: 'Request declined',
      body: `The organizer declined your request to join ${game.title}`,
      data: { type: 'join_request_declined', gameId: String(GAME_ID) },
    });
  });

  // Mocked behavior: decideJoinRequest throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: the organizer approves request 7 for game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not save your answer, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. make saving the answer fail
    const decideJoinRequest = jest.spyOn(joinRequestsDb, 'decideJoinRequest')
      .mockRejectedValue(new Error('database down'));

    // 3. approve it through the interface
    const response = await api.patch(`/api/games/${GAME_ID}/join-requests/${REQUEST_ID}`)
      .set(withToken(token))
      .send({ decision: 'approve' });

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not save your answer, try again later' });
    expect(decideJoinRequest).toHaveBeenCalledWith(GAME_ID, REQUEST_ID, organizer.userId, true);
  });
});
