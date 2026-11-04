import { getGameById } from '../../../../src/game/db/games';
import {
  addGame,
  addJoinRequest,
  addUser,
  asUser,
  cleanUpAfterTests,
  getJoinRequestStatus,
} from '../../helpers';

let organizerId: number;
let requesterId: number;
let otherUserId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
  otherUserId = await addUser('Omar');
});

cleanUpAfterTests();

// Interface PATCH /api/games/{gameId}/join-requests/{requestId}
describe('Unmocked: PATCH /api/games/{gameId}/join-requests/{requestId}', () => {
  // Input: a user who isn't the organizer approves a pending request
  // Expected status code: 403
  // Expected behavior: nothing changes, the request stays pending and the players stay the same
  // Expected output: { msg: "forbidden", error: "Only the organizer can change this game" }
  test('Not the organizer', async () => {
    // 1. a game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);

    // 2. approve it as someone else through the interface
    const response = await asUser(otherUserId).patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .send({ decision: 'approve' });

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can change this game' });
    expect(await getJoinRequestStatus(requestId)).toBe('pending');
    expect((await getGameById(gameId))?.playerCount).toBe(1);
  });
});
