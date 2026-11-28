import * as mysqlDb from '../../../src/core/db/mysql';
import * as gamesDb from '../../../src/game/db/games';
import type * as MockEnv from '../mockEnv';
import {
  api,
  cleanUpAfterTests,
  GAME_ID,
  signIn,
  testGame,
  testOrganizer,
  testPlayer,
  withToken,
} from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/games/{gameId}
describe('Mocked: GET /api/games/{gameId}', () => {
  // Mocked behavior: getGameById returns the game, getGamePlayers an empty list, and
  //   selectRows no rows for findOrganizer's own lookup in the users table, so neither finds
  //   the organizer. Sign in is mocked only so the route can run
  // Input: a signed in user opens game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not load the game, try again later" }
  test('Organizer not found', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testPlayer());

    // 2. a game whose organizer can't be found anywhere
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame());
    jest.spyOn(gamesDb, 'getGamePlayers').mockResolvedValue([]);
    jest.spyOn(mysqlDb, 'selectRows').mockResolvedValue([]);

    // 3. open the game through the interface
    const response = await api.get(`/api/games/${GAME_ID}`).set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load the game, try again later' });
  });

  // Mocked behavior: getGameById returns the game and getGamePlayers its organizer and player
  //   2. selectRows stands in for getGameChatId's lookup and finds no chat for the game, which
  //   the real database never allows since a game's chat is saved with it. Sign in is mocked
  //   only so the route can run
  // Input: player 2 opens game 42
  // Expected status code: 200
  // Expected behavior: the game details are still sent, without a chat to open
  // Expected output: { msg: "success", viewer_role: "player", chat_id: null, ... }
  test('Game has no chat', async () => {
    // 1. sign in as player 2 (not what this test checks)
    const player = testPlayer();
    const token = signIn(player);

    // 2. a game with its organizer and player 2, and no chat
    const organizer = testOrganizer();
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame());
    jest.spyOn(gamesDb, 'getGamePlayers').mockResolvedValue([
      { userId: organizer.userId, firstName: organizer.firstName, lastName: organizer.lastName, noShow: false },
      { userId: player.userId, firstName: player.firstName, lastName: player.lastName, noShow: false },
    ]);
    const selectRows = jest.spyOn(mysqlDb, 'selectRows').mockResolvedValue([]);

    // 3. open the game through the interface
    const response = await api.get(`/api/games/${GAME_ID}`).set(withToken(token));

    // 4. check the details came back without a chat
    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ msg: 'success', viewer_role: 'player', chat_id: null });
    expect(selectRows).toHaveBeenCalledWith(expect.stringContaining('FROM chats WHERE game_id = ?'), [GAME_ID]);
  });

  // Mocked behavior: getGameById throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: a signed in user opens game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not load the game, try again later" }
  test('Database fails', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testPlayer());

    // 2. make reading the game fail
    const getGameById = jest.spyOn(gamesDb, 'getGameById').mockRejectedValue(new Error('database down'));

    // 3. open the game through the interface
    const response = await api.get(`/api/games/${GAME_ID}`).set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load the game, try again later' });
    expect(getGameById).toHaveBeenCalledWith(GAME_ID);
  });
});
