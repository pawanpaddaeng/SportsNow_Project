import {
  addGameAt,
  addJoinRequest,
  addPlayer,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  ONE_DAY_MS,
  SEARCH_LONGITUDE,
  setGameStatus,
} from '../helpers';

// each test's own remote spot, see addGameAt in the helpers
const SEARCH_LATITUDE = 10;
const NOT_SHOWN_LATITUDE = 20;
const FULL_GAME_LATITUDE = 30;
const REQUESTED_LATITUDE = -10;
// about how many degrees of latitude make these distances
const DEGREES_200_M = 0.0018;
const DEGREES_1_KM = 0.009;
const DEGREES_2_KM = 0.018;
// about 60 km of latitude, outside Discover's 50 km limit
const DEGREES_60_KM = 0.54;
const DISTANCE_TOLERANCE_M = 10;
const MAX_PLAYERS = 2;

let userId: number;
let organizerId: number;
let playerId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
  organizerId = await addUser('Omar');
  playerId = await addUser('Paul');
});

cleanUpAfterTests();

// Interface GET /api/games/discover
describe('Unmocked: GET /api/games/discover', () => {
  // Input: a signed in user's location, with three other user's games 200 m, 1 km and 2 km away
  // Expected status code: 200
  // Expected behavior: the games are sorted by distance, nothing changes
  // Expected output: { msg: "success", games } closest first, each with distance_m and
  //   join_request_status null
  test('Nearby games, closest first', async () => {
    // 1. add the games furthest first, so the order can't come from when they were made
    const game2Km = await addGameAt(organizerId, SEARCH_LATITUDE + DEGREES_2_KM, { title: '2 km away' });
    const game1Km = await addGameAt(organizerId, SEARCH_LATITUDE + DEGREES_1_KM, { title: '1 km away' });
    const game200M = await addGameAt(organizerId, SEARCH_LATITUDE + DEGREES_200_M, { title: '200 m away' });

    // 2. discover games through the interface
    const response = await api.get('/api/games/discover').set(asUser(userId))
      .query({ lat: SEARCH_LATITUDE, lng: SEARCH_LONGITUDE });

    // 3. check the order
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    const games: { game: { game_id: number }; distance_m: number; join_request_status: string | null }[] =
      response.body.games;
    expect(games.map((entry) => entry.game.game_id)).toEqual([game200M, game1Km, game2Km]);

    // 4. check each one's distance and request status
    const expectedDistances = new Map([[game200M, 200], [game1Km, 1000], [game2Km, 2000]]);
    games.forEach((entry) => {
      const expectedDistance = expectedDistances.get(entry.game.game_id) ?? 0;
      expect(Math.abs(entry.distance_m - expectedDistance)).toBeLessThan(DISTANCE_TOLERANCE_M);
      expect(entry.join_request_status).toBeNull();
    });
  });

  // Input: a signed in user's location, with a nearby game that has no spots left
  // Expected status code: 200
  // Expected behavior: the full game is still listed so the app can show it as full
  // Expected output: { msg: "success", games } with the game, player_count equal to max_players
  test('Full game still shows', async () => {
    // 1. a game for two players: its organizer and one who joined
    const gameId = await addGameAt(organizerId, FULL_GAME_LATITUDE, { title: 'Full Game', maxPlayers: MAX_PLAYERS });
    await addPlayer(gameId, playerId);

    // 2. discover games through the interface
    const response = await api.get('/api/games/discover').set(asUser(userId))
      .query({ lat: FULL_GAME_LATITUDE, lng: SEARCH_LONGITUDE });

    // 3. check the full game is listed
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.games).toHaveLength(1);
    expect(response.body.games[0].game).toMatchObject({
      game_id: gameId,
      max_players: MAX_PLAYERS,
      player_count: MAX_PLAYERS,
    });
  });

  // Input: a signed in user's location, with only the user's own game, a game they joined,
  //   a cancelled game, a past game and a game 60 km away around it
  // Expected status code: 200
  // Expected behavior: none of the games are listed, nothing changes
  // Expected output: { msg: "no_games" }
  test('Games that should not show', async () => {
    // 1. the user's own game
    await addGameAt(userId, NOT_SHOWN_LATITUDE);

    // 2. a game the user already joined
    const joinedGameId = await addGameAt(organizerId, NOT_SHOWN_LATITUDE);
    await addPlayer(joinedGameId, userId);

    // 3. a cancelled game
    const cancelledGameId = await addGameAt(organizerId, NOT_SHOWN_LATITUDE);
    await setGameStatus(cancelledGameId, 'cancelled');

    // 4. a game that already started
    await addGameAt(organizerId, NOT_SHOWN_LATITUDE, { startsAt: new Date(Date.now() - ONE_DAY_MS) });

    // 5. a game too far away
    await addGameAt(organizerId, NOT_SHOWN_LATITUDE + DEGREES_60_KM);

    // 6. discover games through the interface
    const response = await api.get('/api/games/discover').set(asUser(userId))
      .query({ lat: NOT_SHOWN_LATITUDE, lng: SEARCH_LONGITUDE });

    // 7. check none were listed
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'no_games' });
  });

  // Input: a signed in user's location, where they have a pending join request for a nearby game
  // Expected status code: 200
  // Expected behavior: the game is listed with the user's request status, nothing changes
  // Expected output: { msg: "success", games } with the game, join_request_status "pending"
  test('Request status', async () => {
    // 1. a nearby game the user asked to join
    const gameId = await addGameAt(organizerId, REQUESTED_LATITUDE, { title: 'Requested Game' });
    await addJoinRequest(gameId, userId);

    // 2. discover games through the interface
    const response = await api.get('/api/games/discover').set(asUser(userId))
      .query({ lat: REQUESTED_LATITUDE, lng: SEARCH_LONGITUDE });

    // 3. check the game shows the pending request
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.games).toHaveLength(1);
    expect(response.body.games[0].game.game_id).toBe(gameId);
    expect(response.body.games[0].join_request_status).toBe('pending');
  });

  // Input: a signed in user's request with lat=95&lng=0, lat can't be over 90, then
  //   lat=49&lng=200, lng can't be over 180
  // Expected status code: 400
  // Expected behavior: the request is rejected, nothing changes
  // Expected output: { msg: "invalid_request", error: "lat (-90 to 90) and lng (-180 to 180) are required numbers" }
  test('Location out of range', async () => {
    for (const query of [{ lat: 95, lng: 0 }, { lat: 49, lng: 200 }]) {
      const response = await api.get('/api/games/discover').set(asUser(userId)).query(query);

      expect(response.status).toBe(400);
      expect(response.body).toEqual({
        msg: 'invalid_request',
        error: 'lat (-90 to 90) and lng (-180 to 180) are required numbers',
      });
    }
  });

  // Input: a signed in user's request with lng but no lat in the query
  // Expected status code: 400
  // Expected behavior: the request is rejected, nothing changes
  // Expected output: { msg: "invalid_request", error: "lat (-90 to 90) and lng (-180 to 180) are required numbers" }
  test('No location', async () => {
    const response = await api.get('/api/games/discover').set(asUser(userId))
      .query({ lng: 0 });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'lat (-90 to 90) and lng (-180 to 180) are required numbers',
    });
  });
});
