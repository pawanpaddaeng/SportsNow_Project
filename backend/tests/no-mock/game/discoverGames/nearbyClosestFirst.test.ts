import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
// test files run at the same time on one database and Discover sees everyone's games,
// so each Discover test searches its own remote spot
const SEARCH_LATITUDE = 10;
const SEARCH_LONGITUDE = -150;
// about how many degrees of latitude make these distances
const DEGREES_200_M = 0.0018;
const DEGREES_1_KM = 0.009;
const DEGREES_2_KM = 0.018;
const DISTANCE_TOLERANCE_M = 10;

let userId: number;
let organizerId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `discover-games-test-nearbyClosestFirst-${firstName}`,
    email: 'discover-games-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

// a real upcoming game the given number of degrees north of the search point
function addGame(title: string, degreesNorth: number): Promise<number> {
  return createGame({
    organizerId,
    title,
    sport: 'basketball',
    description: null,
    startsAt: new Date(Date.now() + ONE_DAY_MS),
    endsAt: null,
    locationName: 'Test Field',
    address: '1 Test Rd',
    latitude: SEARCH_LATITUDE + degreesNorth,
    longitude: SEARCH_LONGITUDE,
    maxPlayers: 10,
    skillLevel: 'any',
  });
}

beforeEach(async () => {
  userId = await addUser('Uma');
  organizerId = await addUser('Omar');
});

// deleting the users also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(userId);
  await deleteUser(organizerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface GET /api/games/discover
describe('Unmocked: GET /api/games/discover', () => {
  // Input: a signed in user's location, with three other user's games 200 m, 1 km and 2 km away
  // Expected status code: 200
  // Expected behavior: the games are sorted by distance, nothing changes
  // Expected output: { msg: "success", games } closest first, each with distance_m and
  //   join_request_status null
  test('Nearby games, closest first', async () => {
    // 1. add the games furthest first, so the order can't come from when they were made
    const game2Km = await addGame('2 km away', DEGREES_2_KM);
    const game1Km = await addGame('1 km away', DEGREES_1_KM);
    const game200M = await addGame('200 m away', DEGREES_200_M);

    // 2. discover games through the interface
    const response = await request(createApp())
      .get('/api/games/discover')
      .query({ lat: SEARCH_LATITUDE, lng: SEARCH_LONGITUDE })
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

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
