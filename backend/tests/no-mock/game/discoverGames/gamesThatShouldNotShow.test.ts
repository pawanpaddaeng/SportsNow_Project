import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite } from '../../../../src/core/db/mysql';
import { type NewGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addPlayer, addUser, cleanUpAfterTests, ONE_DAY_MS } from '../../helpers';

// test files run at the same time on one database and Discover sees everyone's games,
// so each Discover test searches its own remote spot
const SEARCH_LATITUDE = 20;
const SEARCH_LONGITUDE = -150;
// about 60 km of latitude, outside Discover's 50 km limit
const DEGREES_60_KM = 0.54;

let userId: number;
let organizerId: number;

// a real game at the search point unless the test says otherwise
function addGameAtSearch(gameOrganizerId: number, overrides: Partial<NewGame> = {}): Promise<number> {
  return addGame(gameOrganizerId, {
    title: 'Hidden Game',
    latitude: SEARCH_LATITUDE,
    longitude: SEARCH_LONGITUDE,
    ...overrides,
  });
}

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
    await addGameAtSearch(userId);

    // 2. a game the user already joined
    const joinedGameId = await addGameAtSearch(organizerId);
    await addPlayer(joinedGameId, userId);

    // 3. a cancelled game
    const cancelledGameId = await addGameAtSearch(organizerId);
    await executeWrite("UPDATE games SET status = 'cancelled' WHERE game_id = ?", [cancelledGameId]);

    // 4. a game that already started
    await addGameAtSearch(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) });

    // 5. a game too far away
    await addGameAtSearch(organizerId, { latitude: SEARCH_LATITUDE + DEGREES_60_KM });

    // 6. discover games through the interface
    const response = await request(createApp())
      .get('/api/games/discover')
      .query({ lat: SEARCH_LATITUDE, lng: SEARCH_LONGITUDE })
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    // 7. check none were listed
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'no_games' });
  });
});
