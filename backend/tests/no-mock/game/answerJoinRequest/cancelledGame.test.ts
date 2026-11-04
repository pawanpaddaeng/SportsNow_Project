import { addGame, addJoinRequest, addUser, asUser, cleanUpAfterTests, setGameStatus } from '../../helpers';

let organizerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface PATCH /api/games/{gameId}/join-requests/{requestId}
describe('Unmocked: PATCH /api/games/{gameId}/join-requests/{requestId}', () => {
  // Input: the organizer approves a request for their cancelled game
  // Expected status code: 409
  // Expected behavior: the answer is rejected
  // Expected output: { msg: "game_cancelled", error: "This game was cancelled" }
  test('Cancelled game', async () => {
    // 1. a cancelled game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);
    await setGameStatus(gameId, 'cancelled');

    // 2. approve it through the interface
    const response = await asUser(organizerId).patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .send({ decision: 'approve' });

    // 3. check it was rejected
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'game_cancelled', error: 'This game was cancelled' });
  });
});
