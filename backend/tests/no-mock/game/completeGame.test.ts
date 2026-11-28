import { selectRows } from '../../../src/core/db/mysql';
import { getGameById } from '../../../src/game/db/games';
import { addGame, addPlayer, addStartedGame, addUser, api, asUser, cleanUpAfterTests, MISSING_GAME_ID, setGameStatus } from '../helpers';
import { type ApiReply, expectGameReply } from '../../sharedHelpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/complete
describe('Unmocked: POST /api/games/{gameId}/complete', () => {
  // Input: the organizer marks their started game as completed
  // Expected status code: 200
  // Expected behavior: the game goes from "scheduled" (in progress) to "completed" in the database
  // Expected output: { msg: "success", game } with game.status "completed"
  test('Organizer marks their started game completed', async () => {
    // 1. the organizer's game that started yesterday
    const gameId = await addStartedGame(organizerId);
    expect((await getGameById(gameId))?.status).toBe('scheduled');

    // 2. mark it completed through the interface
    const response = await api.post(`/api/games/${gameId}/complete`).set(asUser(organizerId));

    // 3. check it was completed
    expectGameReply(response as ApiReply, { game_id: gameId, status: 'completed' });
    expect((await getGameById(gameId))?.status).toBe('completed');
  });

  // Input: the organizer completes their started game with Ben and Cara in it, marking Ben as a
  //   no-show and sending his id twice
  // Expected status code: 200
  // Expected behavior: each player's attendance is saved once, with only Ben as a no-show
  // Expected output: { msg: "success", game } with game.status "completed"
  test('Organizer marks players who did not show up', async () => {
    // 1. the organizer's started game with two players
    const benId = await addUser('Ben');
    const caraId = await addUser('Cara');
    const gameId = await addStartedGame(organizerId);
    await addPlayer(gameId, benId);
    await addPlayer(gameId, caraId);

    // 2. complete it with Ben unticked, his id sent twice
    const response = await api.post(`/api/games/${gameId}/complete`).set(asUser(organizerId))
      .send({ no_show_user_ids: [benId, benId] });

    // 3. check who was saved as a no-show
    expectGameReply(response as ApiReply, { game_id: gameId, status: 'completed' });
    const attendance = await selectRows<{ user_id: number; no_show: number }>(
      'SELECT user_id, no_show FROM game_attendance WHERE game_id = ?', [gameId]);
    const noShows = new Map(attendance.map((row) => [row.user_id, Number(row.no_show)]));
    expect(attendance).toHaveLength(3);
    expect(noShows).toEqual(new Map([[organizerId, 0], [benId, 1], [caraId, 0]]));
  });

  // Input: the organizer completes their started game with a no-show id that isn't a user id
  // Expected status code: 400
  // Expected behavior: the game is left as it was
  // Expected output: { msg: "invalid_request", error: "no_show_user_ids must be a list of user ids" }
  test('A malformed no-show list is rejected', async () => {
    // 1. the organizer's game that started yesterday
    const gameId = await addStartedGame(organizerId);

    // 2. complete it with a malformed list through the interface
    const response = await api.post(`/api/games/${gameId}/complete`).set(asUser(organizerId))
      .send({ no_show_user_ids: ['seven'] });

    // 3. check it was rejected and the game didn't change
    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'no_show_user_ids must be a list of user ids',
    });
    expect((await getGameById(gameId))?.status).toBe('scheduled');
  });

  // Input: a player who isn't the organizer completes the game, the organizer completes a game
  //   no game has the id of, one that was already completed, and one that hasn't started
  // Expected status code: 403, 404, 409 and 409
  // Expected behavior: no game changes
  // Expected output: { msg: "forbidden" }, { msg: "not_found" }, { msg: "game_completed" } and
  //   { msg: "game_not_started" }
  test('Games that cannot be completed are turned down', async () => {
    const playerId = await addUser('Paul');
    const startedId = await addStartedGame(organizerId);
    await addPlayer(startedId, playerId);
    const completedId = await addStartedGame(organizerId);
    await setGameStatus(completedId, 'completed');
    const upcomingId = await addGame(organizerId);

    const cases: [number, number, number, string][] = [
      [startedId, playerId, 403, 'forbidden'],
      [MISSING_GAME_ID, organizerId, 404, 'not_found'],
      [completedId, organizerId, 409, 'game_completed'],
      [upcomingId, organizerId, 409, 'game_not_started'],
    ];
    for (const [gameId, userId, status, msg] of cases) {
      const response = await api.post(`/api/games/${gameId}/complete`).set(asUser(userId));
      expect(response.status).toBe(status);
      expect(response.body).toMatchObject({ msg });
    }
    expect((await getGameById(startedId))?.status).toBe('scheduled');
    expect((await getGameById(upcomingId))?.status).toBe('scheduled');
  });
});
