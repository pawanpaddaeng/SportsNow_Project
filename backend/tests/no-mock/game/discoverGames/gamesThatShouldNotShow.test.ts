import {
  addGameAt,
  addPlayer,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  ONE_DAY_MS,
  SEARCH_LONGITUDE,
  setGameStatus,
} from '../../helpers';

// this test's own remote spot, see addGameAt in the helpers
const SEARCH_LATITUDE = 20;
// about 60 km of latitude, outside Discover's 50 km limit
const DEGREES_60_KM = 0.54;

let userId: number;
let organizerId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
  organizerId = await addUser('Omar');
});

cleanUpAfterTests();

// Interface GET /api/games/discover
describe('Unmocked: GET /api/games/discover', () => {
  // Input: a signed in user's location, with only the user's own game, a game they joined,
  //   a cancelled game, a past game and a game 60 km away around it
  // Expected status code: 200
  // Expected behavior: none of the games are listed, nothing changes
  // Expected output: { msg: "no_games" }
  test('Games that should not show', async () => {
    // 1. the user's own game
    await addGameAt(userId, SEARCH_LATITUDE);

    // 2. a game the user already joined
    const joinedGameId = await addGameAt(organizerId, SEARCH_LATITUDE);
    await addPlayer(joinedGameId, userId);

    // 3. a cancelled game
    const cancelledGameId = await addGameAt(organizerId, SEARCH_LATITUDE);
    await setGameStatus(cancelledGameId, 'cancelled');

    // 4. a game that already started
    await addGameAt(organizerId, SEARCH_LATITUDE, { startsAt: new Date(Date.now() - ONE_DAY_MS) });

    // 5. a game too far away
    await addGameAt(organizerId, SEARCH_LATITUDE + DEGREES_60_KM);

    // 6. discover games through the interface
    const response = await api.get('/api/games/discover').set(asUser(userId))
      .query({ lat: SEARCH_LATITUDE, lng: SEARCH_LONGITUDE });

    // 7. check none were listed
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'no_games' });
  });
});
