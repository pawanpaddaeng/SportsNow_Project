import {
  addGame,
  addJoinRequest,
  addUser,
  asUser,
  cleanUpAfterTests,
  getJoinRequestStatus,
  getPlayerIds,
} from '../../helpers';

let organizerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface PATCH /api/games/{gameId}/join-requests/{requestId}
describe('Unmocked: PATCH /api/games/{gameId}/join-requests/{requestId}', () => {
  // Input: the organizer sends { decision: "decline" } for a pending request
  // Expected status code: 200
  // Expected behavior: the request is declined, the players don't change
  // Expected output: { msg: "success", request, game } with status "declined" and player_count still 1
  test('Decline', async () => {
    // 1. a game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);

    // 2. decline it through the interface
    const response = await asUser(organizerId).patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .send({ decision: 'decline' });

    // 3. check the reply
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.request).toEqual({ request_id: requestId, status: 'declined' });
    expect(response.body.game).toMatchObject({ game_id: gameId, player_count: 1 });

    // 4. check what was really saved
    expect(await getJoinRequestStatus(requestId)).toBe('declined');
    expect(await getPlayerIds(gameId)).toEqual([organizerId]);
  });
});
