import {
  addGame,
  addJoinRequest,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  getJoinRequestStatus,
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
  // Input: { decision: "maybe" } for a real request, and a valid decision with a bad request id
  // Expected status code: 400
  // Expected behavior: the real request stays pending
  // Expected output: { msg: "invalid_request", error } naming the bad input
  test.each([
    ['decision "maybe"', 'maybe', false, 'decision must be "approve" or "decline"'],
    ['a bad request id', 'approve', true, 'gameId and requestId must be positive whole numbers'],
  ] as const)('Bad input: %s', async (_name, decision, useBadRequestId, error) => {
    // 1. a game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);

    // 2. send the bad input through the interface
    const response = await api.patch(`/api/games/${gameId}/join-requests/${useBadRequestId ? 'abc' : String(requestId)}`)
      .set(asUser(organizerId))
      .send({ decision });

    // 3. check it was rejected and the request is untouched
    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error });
    expect(await getJoinRequestStatus(requestId)).toBe('pending');
  });
});
