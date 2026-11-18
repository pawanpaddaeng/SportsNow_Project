import { executeWrite, selectRows } from '../../../src/core/db/mysql';
import {
  addCancelledGame,
  addGame,
  addJoinRequest,
  addPlayer,
  addStartedGame,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  getJoinRequestIds,
  MISSING_GAME_ID,
} from '../helpers';
import { waitForNotices } from '../notification/notificationHelpers';

const TOO_LONG_MESSAGE_LENGTH = 201;

let organizerId: number;
let requesterId: number;
let playerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
  playerId = await addUser('Paul');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: skill_level, play_frequency and message for another user's game
  // Expected status code: 201
  // Expected behavior: a pending request is saved with the answers, and the organizer gets a
  //   join_request notification for the game saved for their Notifications screen
  // Expected output: { msg: "success", request } with the request's id, game_id and status "pending"
  test('Valid request', async () => {
    // 1. another user's upcoming game
    const gameId = await addGame(organizerId);

    // 2. ask to join through the interface
    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'intermediate', play_frequency: 'weekly', message: 'Happy to play any position' });

    // 3. check the reply
    expect(response.status).toBe(201);
    expect(response.body.msg).toBe('success');
    expect(response.body.request).toMatchObject({ game_id: gameId, status: 'pending' });

    // 4. check what was really saved
    const rows = await selectRows<Record<string, unknown>>(
      `SELECT request_id, skill_level, play_frequency, message, status
       FROM join_requests WHERE game_id = ? AND user_id = ?`,
      [gameId, requesterId],
    );
    expect(rows).toEqual([{
      request_id: response.body.request.request_id,
      skill_level: 'intermediate',
      play_frequency: 'weekly',
      message: 'Happy to play any position',
      status: 'pending',
    }]);
    expect(await waitForNotices(organizerId)).toEqual([{
      type: 'join_request',
      title: 'New join request',
      body: 'Rae wants to join Evening Basketball',
      game_id: gameId,
    }]);
  });

  // Input: { skill_level: "beginner" } for another user's game
  // Expected status code: 201
  // Expected behavior: the request is saved, with play_frequency and message empty
  // Expected output: { msg: "success", request } with status "pending"
  test('Only skill level', async () => {
    // 1. another user's upcoming game
    const gameId = await addGame(organizerId);

    // 2. ask to join with only the required answer through the interface
    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'beginner' });

    // 3. check the reply
    expect(response.status).toBe(201);
    expect(response.body.request).toMatchObject({ game_id: gameId, status: 'pending' });

    // 4. check what was really saved
    const rows = await selectRows<Record<string, unknown>>(
      'SELECT skill_level, play_frequency, message, status FROM join_requests WHERE game_id = ? AND user_id = ?',
      [gameId, requesterId],
    );
    expect(rows).toEqual([{ skill_level: 'beginner', play_frequency: null, message: null, status: 'pending' }]);
  });

  // Input: new answers from a user whose earlier request for the game was declined
  // Expected status code: 201
  // Expected behavior: the same request goes back to pending with the new answers, no second one is added
  // Expected output: { msg: "success", request } with the same request id and status "pending"
  test('Ask again', async () => {
    // 1. a game the user's earlier request was declined for
    const gameId = await addGame(organizerId);
    const declined = await executeWrite(
      `INSERT INTO join_requests (game_id, user_id, skill_level, play_frequency, message, status)
       VALUES (?, ?, 'beginner', 'rarely', 'First try', 'declined')`,
      [gameId, requesterId],
    );

    // 2. ask again with new answers through the interface
    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'advanced', play_frequency: 'weekly', message: 'Second try' });

    // 3. check the reply
    expect(response.status).toBe(201);
    expect(response.body.request).toMatchObject({ request_id: declined.insertId, status: 'pending' });

    // 4. check there's still one request, pending with the new answers
    const rows = await selectRows<Record<string, unknown>>(
      `SELECT request_id, skill_level, play_frequency, message, status
       FROM join_requests WHERE game_id = ? AND user_id = ?`,
      [gameId, requesterId],
    );
    expect(rows).toEqual([{
      request_id: declined.insertId,
      skill_level: 'advanced',
      play_frequency: 'weekly',
      message: 'Second try',
      status: 'pending',
    }]);
  });

  // Input: no skill_level, an unknown play_frequency, or a message of 201 characters
  // Expected status code: 400
  // Expected behavior: nothing is saved
  // Expected output: { msg: "invalid_request", error } naming the bad field
  test.each([
    ['no skill_level', {}, 'skill_level must be one of: beginner, intermediate, advanced, competitive'],
    [
      'an unknown play_frequency',
      { skill_level: 'beginner', play_frequency: 'daily' },
      'play_frequency must be one of: rarely, monthly, weekly, several_per_week',
    ],
    [
      'a message of 201 characters',
      { skill_level: 'beginner', message: 'x'.repeat(TOO_LONG_MESSAGE_LENGTH) },
      'message must be text (max 200 characters)',
    ],
  ])('Bad answers: %s', async (_name, body, error) => {
    const gameId = await addGame(organizerId);

    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error });
    expect(await getJoinRequestIds(gameId)).toEqual([]);
  });

  // Input: a second request while the user's first is still pending
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "already_requested", error } saying to wait for the organizer
  test('Already asked', async () => {
    const gameId = await addGame(organizerId);
    await addJoinRequest(gameId, requesterId);

    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'advanced' });

    expect(response.status).toBe(409);
    expect(response.body).toEqual({
      msg: 'already_requested',
      error: 'You already asked to join this game, wait for the organizer to respond',
    });
  });

  // Input: a user who is already in the game asks to join it
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "already_player", error: "You're already in this game" }
  test('Already a player', async () => {
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);

    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(playerId))
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'already_player', error: "You're already in this game" });
  });

  // Input: a user asks to join a cancelled game, and a game that already started
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "game_cancelled" } and { msg: "game_started" }
  test.each([
    ['cancelled', 'game_cancelled', () => addCancelledGame(organizerId)],
    ['started', 'game_started', () => addStartedGame(organizerId)],
  ] as const)('Asking to join a %s game', async (_name, msg, setUpGame) => {
    const gameId = await setUpGame();

    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
  });

  // Input: a user asks to join a game with no spots left
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "game_full", error: "This game is full" }
  test('Game is full', async () => {
    // 1. a game for two players: its organizer and one who joined
    const gameId = await addGame(organizerId, { maxPlayers: 2 });
    await addPlayer(gameId, playerId);

    // 2. ask to join it through the interface
    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'beginner' });

    // 3. check it was rejected
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'game_full', error: 'This game is full' });
  });

  // Input: the organizer asks to join their own game
  // Expected status code: 409
  // Expected behavior: nothing is saved
  // Expected output: { msg: "own_game", error: "You organize this game" }
  test('Own game', async () => {
    const gameId = await addGame(organizerId);

    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(organizerId))
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'own_game', error: 'You organize this game' });
    expect(await getJoinRequestIds(gameId)).toEqual([]);
  });

  // Input: a signed in user asks to join an id with no game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "not_found", error: "This game does not exist" }
  test("Game doesn't exist", async () => {
    const response = await api.post(`/api/games/${MISSING_GAME_ID}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'not_found', error: 'This game does not exist' });
  });
});
