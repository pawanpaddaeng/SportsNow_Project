import { getGameById } from '../../../../src/game/db/games';
import {
  addGame,
  addJoinRequest,
  addPlayer,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  getJoinRequestStatus,
} from '../../helpers';

let organizerId: number;
let playerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface PATCH /api/games/{gameId}/join-requests/{requestId}
describe('Unmocked: PATCH /api/games/{gameId}/join-requests/{requestId}', () => {
  // Input: the organizer approves a pending request when no spots are left
  // Expected status code: 409
  // Expected behavior: nothing changes, the request stays pending and the players stay the same
  // Expected output: { msg: "game_full", error: "This game is full, add spots before approving more players" }
  test('Game is full', async () => {
    // 1. a full game for two players, with a pending request
    const gameId = await addGame(organizerId, { maxPlayers: 2 });
    await addPlayer(gameId, playerId);
    const requestId = await addJoinRequest(gameId, requesterId);

    // 2. approve it through the interface
    const response = await api.patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .set(asUser(organizerId))
      .send({ decision: 'approve' });

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(409);
    expect(response.body).toEqual({
      msg: 'game_full',
      error: 'This game is full, add spots before approving more players',
    });
    expect(await getJoinRequestStatus(requestId)).toBe('pending');
    expect((await getGameById(gameId))?.playerCount).toBe(2);
  });
});
