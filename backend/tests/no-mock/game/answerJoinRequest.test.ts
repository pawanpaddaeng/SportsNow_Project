import { getGameById } from '../../../src/game/db/games';
import { createAuthToken } from '../../../src/user/auth/jwt';
import {
  addGame,
  addJoinRequest,
  addPlayer,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  getJoinRequestStatus,
  getPlayerIds,
  setGameStatus,
  withToken,
} from '../helpers';
import { waitForNotices } from '../notification/notificationHelpers';

let organizerId: number;
let playerId: number;
let requesterId: number;
let otherUserId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  requesterId = await addUser('Rae');
  otherUserId = await addUser('Omar');
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

  // Input: the organizer sends { decision: "approve" } for a pending request
  // Expected status code: 200
  // Expected behavior: the request is approved and the person joins the game, and they get a
  //   join_request_approved notification for the game saved for their Notifications screen
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
    expect(await waitForNotices(requesterId)).toEqual([{
      type: 'join_request_approved',
      title: 'Request approved',
      body: "You're in! You joined Evening Basketball",
      game_id: gameId,
    }]);
  });

  // Input: the organizer sends { decision: "decline" } for a pending request
  // Expected status code: 200
  // Expected behavior: the request is declined, the players don't change, and the person gets
  //   a join_request_declined notification for the game saved for their Notifications screen
  // Expected output: { msg: "success", request, game } with status "declined" and player_count still 1
  test('Decline', async () => {
    // 1. a game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);

    // 2. decline it through the interface
    const response = await api.patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .set(asUser(organizerId))
      .send({ decision: 'decline' });

    // 3. check the reply
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.request).toEqual({ request_id: requestId, status: 'declined' });
    expect(response.body.game).toMatchObject({ game_id: gameId, player_count: 1 });

    // 4. check what was really saved
    expect(await getJoinRequestStatus(requestId)).toBe('declined');
    expect(await getPlayerIds(gameId)).toEqual([organizerId]);
    expect(await waitForNotices(requesterId)).toEqual([{
      type: 'join_request_declined',
      title: 'Request declined',
      body: 'The organizer declined your request to join Evening Basketball',
      game_id: gameId,
    }]);
  });

  // Input: the organizer answers the same request twice
  // Expected status code: 200 the first time, then 409
  // Expected behavior: the second answer is rejected
  // Expected output: { msg: "already_responded", error: "This request was already answered or withdrawn" }
  //   the second time
  test('Already answered', async () => {
    // 1. a game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);
    const token = createAuthToken(organizerId);

    // 2. answer it once through the interface
    const first = await api.patch(`/api/games/${gameId}/join-requests/${requestId}`).set(withToken(token))
      .send({ decision: 'decline' });
    expect(first.status).toBe(200);

    // 3. answer it again and check that's rejected
    const second = await api.patch(`/api/games/${gameId}/join-requests/${requestId}`).set(withToken(token))
      .send({ decision: 'approve' });
    expect(second.status).toBe(409);
    expect(second.body).toEqual({
      msg: 'already_responded',
      error: 'This request was already answered or withdrawn',
    });
  });

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
    const response = await api.patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .set(asUser(organizerId))
      .send({ decision: 'approve' });

    // 3. check it was rejected
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'game_cancelled', error: 'This game was cancelled' });
  });

  // Input: a user who isn't the organizer approves a pending request
  // Expected status code: 403
  // Expected behavior: nothing changes, the request stays pending and the players stay the same
  // Expected output: { msg: "forbidden", error: "Only the organizer can change this game" }
  test('Not the organizer', async () => {
    // 1. a game with a pending request
    const gameId = await addGame(organizerId);
    const requestId = await addJoinRequest(gameId, requesterId);

    // 2. approve it as someone else through the interface
    const response = await api.patch(`/api/games/${gameId}/join-requests/${requestId}`)
      .set(asUser(otherUserId))
      .send({ decision: 'approve' });

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can change this game' });
    expect(await getJoinRequestStatus(requestId)).toBe('pending');
    expect((await getGameById(gameId))?.playerCount).toBe(1);
  });

  // Input: the organizer answers one game's id with a request id from their other game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "request_not_found", error: "This join request does not exist" }
  test("Request doesn't exist", async () => {
    // 1. two games, and a request for the second one
    const gameId = await addGame(organizerId, { title: 'First Game' });
    const otherGameId = await addGame(organizerId, { title: 'Second Game' });
    const otherRequestId = await addJoinRequest(otherGameId, requesterId);

    // 2. answer it under the first game through the interface
    const response = await api.patch(`/api/games/${gameId}/join-requests/${otherRequestId}`)
      .set(asUser(organizerId))
      .send({ decision: 'approve' });

    // 3. check it wasn't found
    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'request_not_found', error: 'This join request does not exist' });
  });
});
