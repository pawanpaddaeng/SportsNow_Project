import * as push from '../../../../src/core/notifications/push';
import * as gamesDb from '../../../../src/game/db/games';
import * as joinRequestsDb from '../../../../src/game/db/joinRequests';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, GAME_ID, signIn, testGame, testOrganizer, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const APPROVED_REQUEST_ID = 7;
const DECLINED_REQUEST_ID = 8;
const APPROVED_USER_ID = 2;
const DECLINED_USER_ID = 3;

cleanUpAfterTests();

// Interface PATCH /api/games/{gameId}/join-requests/{requestId}
describe('Mocked: PATCH /api/games/{gameId}/join-requests/{requestId}', () => {
  // Mocked behavior: decideJoinRequest saves nothing and reports whose request was answered,
  //   and getGameById returns the game. sendPushToUser is replaced to check who is told.
  //   Sign in is mocked only so the route can run
  // Input: the organizer approves one request and declines another
  // Expected status code: 200 for both
  // Expected behavior: the first person gets join_request_approved and the second gets join_request_declined
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
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    // 3. approve the first through the interface
    const approved = await withToken(token).patch(`/api/games/${GAME_ID}/join-requests/${APPROVED_REQUEST_ID}`)
      .send({ decision: 'approve' });

    // 4. decline the second through the interface
    const declined = await withToken(token).patch(`/api/games/${GAME_ID}/join-requests/${DECLINED_REQUEST_ID}`)
      .send({ decision: 'decline' });

    // 5. check the replies and who was told what
    expect(approved.status).toBe(200);
    expect(approved.body.request).toEqual({ request_id: APPROVED_REQUEST_ID, status: 'approved' });
    expect(declined.status).toBe(200);
    expect(declined.body.request).toEqual({ request_id: DECLINED_REQUEST_ID, status: 'declined' });
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
});
