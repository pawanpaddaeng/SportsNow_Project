import * as push from '../../../../src/core/notifications/push';
import * as joinRequestsDb from '../../../../src/game/db/joinRequests';
import type * as MockEnv from '../../mockEnv';
import {
  api,
  cleanUpAfterTests,
  GAME_ID,
  signIn,
  testGame,
  testOrganizer,
  testPlayer,
  withToken,
} from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const REQUEST_ID = 7;

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Mocked: POST /api/games/{gameId}/join-requests', () => {
  // Mocked behavior: createJoinRequest saves nothing and reports a new request.
  //   sendPushToUser is replaced to check who is told. Sign in is mocked only so the route can run
  // Input: a valid request for game 42
  // Expected status code: 201
  // Expected behavior: the organizer gets a join_request notification with the game's id
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
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    // 3. ask to join through the interface
    const response = await api.post(`/api/games/${GAME_ID}/join-requests`).set(withToken(token))
      .send({ skill_level: 'beginner' });

    // 4. check the reply and the notification
    expect(response.status).toBe(201);
    expect(response.body).toEqual({
      msg: 'success',
      request: { request_id: REQUEST_ID, game_id: GAME_ID, status: 'pending' },
    });
    expect(sendPushToUser).toHaveBeenCalledTimes(1);
    expect(sendPushToUser).toHaveBeenCalledWith(organizer.userId, {
      title: 'New join request',
      body: `${player.firstName} wants to join ${game.title}`,
      data: { type: 'join_request', gameId: String(GAME_ID) },
    });
  });
});
