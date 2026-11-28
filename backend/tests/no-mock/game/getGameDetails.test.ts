import {
  addGame,
  addJoinRequest,
  addPlayer,
  addStartedGame,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  MISSING_GAME_ID,
  setGameStatus,
} from '../helpers';

let organizerId: number;
let requesterIds: number[];
let playerId: number;
let viewerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterIds = [await addUser('Rae'), await addUser('Rob')];
  playerId = await addUser('Paul');
  viewerId = await addUser('Vera');
});

cleanUpAfterTests();

// Interface GET /api/games/{gameId}
describe('Unmocked: GET /api/games/{gameId}', () => {
  // Input: the organizer opens their game, which has two pending join requests
  // Expected status code: 200
  // Expected behavior: the organizer sees their role and the waiting requests, nothing changes
  // Expected output: { msg: "success", game, organizer, players, ... } with viewer_role
  //   "organizer" and pending_requests 2
  test('Organizer', async () => {
    // 1. a game with two pending join requests
    const gameId = await addGame(organizerId);
    await Promise.all(requesterIds.map((requesterId) => addJoinRequest(gameId, requesterId)));

    // 2. open the game through the interface
    const response = await api.get(`/api/games/${gameId}`).set(asUser(organizerId));

    // 3. check the organizer's view
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game.game_id).toBe(gameId);
    expect(response.body.organizer.user_id).toBe(organizerId);
    expect(response.body.viewer_role).toBe('organizer');
    expect(response.body.pending_requests).toBe(2);
  });

  // Input: a user opens a game they joined
  // Expected status code: 200
  // Expected behavior: the user sees their role in the game, nothing changes
  // Expected output: { msg: "success", game, organizer, players, ... } with viewer_role "player"
  test('Player', async () => {
    // 1. a game the user joined
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);

    // 2. open the game through the interface
    const response = await api.get(`/api/games/${gameId}`).set(asUser(playerId));

    // 3. check the player's view
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game.game_id).toBe(gameId);
    expect(response.body.viewer_role).toBe('player');
  });

  // Input: a user opens a game they asked to join
  // Expected status code: 200
  // Expected behavior: the user sees they are only viewing it and their request, nothing changes
  // Expected output: { msg: "success", game, organizer, players, ... } with viewer_role
  //   "viewer" and join_request_status "pending"
  test('Viewer', async () => {
    // 1. a game the user asked to join
    const gameId = await addGame(organizerId);
    await addJoinRequest(gameId, viewerId);

    // 2. open the game through the interface
    const response = await api.get(`/api/games/${gameId}`).set(asUser(viewerId));

    // 3. check the viewer's view
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game.game_id).toBe(gameId);
    expect(response.body.viewer_role).toBe('viewer');
    expect(response.body.join_request_status).toBe('pending');
  });

  // Input: the organizer left their completed game, and Paul, still in it, opens it
  // Expected status code: 200
  // Expected behavior: the organizer isn't one of the players any more, so they're looked up
  //   by their account, nothing changes
  // Expected output: { msg: "success", organizer, ... } with the organizer's id and name
  test('Organizer who left a completed game is still shown', async () => {
    const gameId = await addStartedGame(organizerId);
    await addPlayer(gameId, playerId);
    await setGameStatus(gameId, 'completed');
    expect((await api.post(`/api/games/${gameId}/leave`).set(asUser(organizerId))).status).toBe(200);

    const response = await api.get(`/api/games/${gameId}`).set(asUser(playerId));

    expect(response.status).toBe(200);
    expect(response.body.organizer).toEqual({ user_id: organizerId, first_name: 'Olivia', last_name: 'Tester', no_show: false });
    expect(response.body.players.map((player: { user_id: number }) => player.user_id)).toEqual([playerId]);
  });

  // Input: a user opens a game they never asked to join
  // Expected status code: 200
  // Expected behavior: the user only views it, nothing changes
  // Expected output: { msg: "success", ... } with viewer_role "viewer" and join_request_status null
  test('Viewer who never asked to join', async () => {
    const gameId = await addGame(organizerId);

    const response = await api.get(`/api/games/${gameId}`).set(asUser(viewerId));

    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ viewer_role: 'viewer', join_request_status: null });
  });

  // Input: a signed in user opens /api/games/abc
  // Expected status code: 400
  // Expected behavior: the request is rejected, nothing changes
  // Expected output: { msg: "invalid_request", error: "gameId must be a positive whole number" }
  test('Bad id', async () => {
    const response = await api.get('/api/games/abc').set(asUser(viewerId));

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'gameId must be a positive whole number' });
  });

  // Input: a signed in user opens an id with no game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "not_found", error: "This game does not exist" }
  test("Game doesn't exist", async () => {
    const response = await api.get(`/api/games/${MISSING_GAME_ID}`).set(asUser(viewerId));

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'not_found', error: 'This game does not exist' });
  });
});
