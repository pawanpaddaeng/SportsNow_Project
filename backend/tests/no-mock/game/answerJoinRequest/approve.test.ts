import {
  addGame,
  addJoinRequest,
  addUser,
  api,
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
  // Input: the organizer sends { decision: "approve" } for a pending request
  // Expected status code: 200
  // Expected behavior: the request is approved and the person joins the game
  // Expected output: { msg: "success", request, game } with status "approved" and player_count up by one
  test('Approve', async () => {
    // 1. a game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);

    // 2. approve it through the interface
    const response = await api.patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .set(asUser(organizerId))
      .send({ decision: 'approve' });

    // 3. check the reply
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.request).toEqual({ request_id: requestId, status: 'approved' });
    expect(response.body.game).toMatchObject({ game_id: gameId, player_count: 2 });

    // 4. check what was really saved
    expect(await getJoinRequestStatus(requestId)).toBe('approved');
    expect(await getPlayerIds(gameId)).toContain(requesterId);
  });
});
