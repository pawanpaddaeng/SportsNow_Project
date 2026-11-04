import { addGameAt, addUser, api, asUser, cleanUpAfterTests, SEARCH_LONGITUDE } from '../../helpers';

// this test's own remote spot, see addGameAt in the helpers
const SEARCH_LATITUDE = 10;
// about how many degrees of latitude make these distances
const DEGREES_200_M = 0.0018;
const DEGREES_1_KM = 0.009;
const DEGREES_2_KM = 0.018;
const DISTANCE_TOLERANCE_M = 10;

let userId: number;
let organizerId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
  organizerId = await addUser('Omar');
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
});
