import {
  addGameAt,
  addPlayer,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  SEARCH_LONGITUDE,
} from '../../helpers';

// this test's own remote spot, see addGameAt in the helpers
const SEARCH_LATITUDE = 30;
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
  // Input: a signed in user's location, with a nearby game that has no spots left
  // Expected status code: 200
  // Expected behavior: the full game is still listed so the app can show it as full
  // Expected output: { msg: "success", games } with the game, player_count equal to max_players
  test('Full game still shows', async () => {
    // 1. a game for two players: its organizer and one who joined
    const gameId = await addGameAt(organizerId, SEARCH_LATITUDE, { title: 'Full Game', maxPlayers: MAX_PLAYERS });
    await addPlayer(gameId, playerId);

    // 2. discover games through the interface
    const response = await api.get('/api/games/discover').set(asUser(userId))
      .query({ lat: SEARCH_LATITUDE, lng: SEARCH_LONGITUDE });

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
});
