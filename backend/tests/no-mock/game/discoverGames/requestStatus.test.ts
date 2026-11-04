import {
  addGameAt,
  addJoinRequest,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  SEARCH_LONGITUDE,
} from '../../helpers';

// this test's own remote spot, see addGameAt in the helpers
const SEARCH_LATITUDE = -10;

let userId: number;
let organizerId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
  organizerId = await addUser('Omar');
});

cleanUpAfterTests();

// Interface GET /api/games/discover
describe('Unmocked: GET /api/games/discover', () => {
  // Input: a signed in user's location, where they have a pending join request for a nearby game
  // Expected status code: 200
  // Expected behavior: the game is listed with the user's request status, nothing changes
  // Expected output: { msg: "success", games } with the game, join_request_status "pending"
  test('Request status', async () => {
    // 1. a nearby game the user asked to join
    const gameId = await addGameAt(organizerId, SEARCH_LATITUDE, { title: 'Requested Game' });
    await addJoinRequest(gameId, userId);

    // 2. discover games through the interface
    const response = await api.get('/api/games/discover').set(asUser(userId))
      .query({ lat: SEARCH_LATITUDE, lng: SEARCH_LONGITUDE });

    // 3. check the game shows the pending request
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.games).toHaveLength(1);
    expect(response.body.games[0].game.game_id).toBe(gameId);
    expect(response.body.games[0].join_request_status).toBe('pending');
  });
});
