import { performance } from 'perf_hooks';

import { executeWrite, placeholders, type SqlValue } from '../../src/core/db/mysql';
import { logInfo } from '../../src/core/logger';
import { addUser, api, asUser, cleanUpAfterTests, ONE_DAY_MS, SEARCH_LONGITUDE } from '../no-mock/helpers';

/*  NFR 1 (Performance): Discover should show nearby games in under 5 seconds from when the
    user selects the compass. This checks the server's share: GET /api/games/discover answers
    in under 1 second with 1,000 nearby games, leaving the rest for the network and the phone.
 */

// this test's own remote spot; the other Discover tests use latitudes 10, 20, 30 and -10
const SEED_LATITUDE = 40;
const GAME_COUNT = 1000;
// about 45 km of latitude, inside Discover's 50 km limit
const DEGREES_45_KM = 0.4;
const RUNS = 10;
const LIMIT_MS = 1000;
const MAX_DISCOVER_GAMES = 50;
const MAX_DISCOVER_DISTANCE_M = 50_000;
// a prime, so stepping by it visits every game once in a scrambled order
const SCRAMBLE_STEP = 7919;
const COLUMNS_PER_GAME = 12;

// seeding 1,000 games and making 10 calls takes longer than Jest's default 5 seconds
jest.setTimeout(60_000);

cleanUpAfterTests();

/*  one INSERT for all the games, spread evenly from the search point out to 45 km north of it.
    They're saved in a scrambled order so the closest-first order can't come from game_id
 */
function seedGamesQuery(organizerId: number): { sql: string; params: SqlValue[] } {
  const startsAt = new Date(Date.now() + ONE_DAY_MS).toISOString().slice(0, 19).replace('T', ' ');
  const params: SqlValue[] = [];
  for (let index = 0; index < GAME_COUNT; index++) {
    const position = (index * SCRAMBLE_STEP) % GAME_COUNT;
    const latitude = SEED_LATITUDE + (DEGREES_45_KM * (position + 1)) / GAME_COUNT;
    params.push(organizerId, `Seeded Game ${position}`, 'basketball', null, startsAt, null,
      'Seeded Court', '1 Test Road', latitude, SEARCH_LONGITUDE, 10, 'any');
  }
  const rows = Array.from({ length: GAME_COUNT }, () => `(${placeholders(COLUMNS_PER_GAME)})`).join(', ');
  return {
    sql: `INSERT INTO games (organizer_id, title, sport, description, starts_at, ends_at,
                             location_name, address, latitude, longitude, max_players, skill_level)
          VALUES ${rows}`,
    params,
  };
}

// Interface GET /api/games/discover
describe('NFR 1: GET /api/games/discover performance', () => {
  // Input: a signed in user's location, with 1,000 other user's upcoming games within 45 km
  // Expected status code: 200, on each of 10 calls
  // Expected behavior: each call answers in under 1 second, nothing changes
  // Expected output: { msg: "success", games } with the closest 50 games, closest first
  test('Discover answers in under 1 second with 1,000 nearby games', async () => {
    // 1. a user searching, and 1,000 games around them by another user
    const userId = await addUser('Searcher');
    const organizerId = await addUser('Organiser');
    const { sql, params } = seedGamesQuery(organizerId);
    await executeWrite(sql, params);

    // 2. discover games through the interface several times, timing each call
    const timesMs: number[] = [];
    for (let run = 0; run < RUNS; run++) {
      const start = performance.now();
      const response = await api.get('/api/games/discover').set(asUser(userId))
        .query({ lat: SEED_LATITUDE, lng: SEARCH_LONGITUDE });
      timesMs.push(performance.now() - start);

      // 3. check the answer is still right: the closest 50, closest first
      expect(response.status).toBe(200);
      expect(response.body.msg).toBe('success');
      const distances = (response.body.games as { distance_m: number }[]).map((entry) => entry.distance_m);
      expect(distances).toHaveLength(MAX_DISCOVER_GAMES);
      expect(distances).toEqual([...distances].sort((a, b) => a - b));
      expect(Math.max(...distances)).toBeLessThanOrEqual(MAX_DISCOVER_DISTANCE_M);
    }

    // 4. check every call was fast enough
    const slowestMs = Math.max(...timesMs);
    const averageMs = timesMs.reduce((sum, time) => sum + time, 0) / RUNS;
    logInfo(`Discover with ${GAME_COUNT} games: average ${averageMs.toFixed(0)} ms, ` +
      `slowest ${slowestMs.toFixed(0)} ms over ${RUNS} calls`);
    expect(slowestMs).toBeLessThan(LIMIT_MS);
  });
});
