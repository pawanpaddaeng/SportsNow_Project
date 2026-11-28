import { selectRows } from '../../../src/core/db/mysql';
import { getGameById } from '../../../src/game/db/games';
import {
  addCancelledGame,
  addGame,
  addPlayer,
  addStartedGame,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  getPlayerIds,
  MISSING_GAME_ID,
  setGameStatus,
} from '../helpers';

let organizerId: number;
let playerId: number;
let otherPlayerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  otherPlayerId = await addUser('Pia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/leave
describe('Unmocked: POST /api/games/{gameId}/leave', () => {
  // Input: a player leaves an upcoming game, which also has another player in it
  // Expected status code: 200
  // Expected behavior: the player is no longer in the game; the other player still is,
  //   and the game still exists
  // Expected output: { msg: "success", game, game_deleted: false } with the game's
  //   player_count down by one
  test('Player leaves a game that has another player', async () => {
    // 1. an upcoming game with the organizer and two players
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);
    await addPlayer(gameId, otherPlayerId);
    expect((await getGameById(gameId))?.playerCount).toBe(3);

    // 2. leave it through the interface
    const response = await api.post(`/api/games/${gameId}/leave`).set(asUser(playerId));

    // 3. check the reply
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game_deleted).toBe(false);
    expect(response.body.game).toMatchObject({ game_id: gameId, player_count: 2 });

    // 4. check only that player left, and the game still exists
    expect(await getGameById(gameId)).not.toBeNull();
    const playerIds = await getPlayerIds(gameId);
    expect(playerIds).toHaveLength(2);
    expect(playerIds).toEqual(expect.arrayContaining([organizerId, otherPlayerId]));
  });

  // Input: the organizer, the only player left in their cancelled game, leaves it
  //   (the organizer is always a player, and can only leave once the game is cancelled or completed)
  // Expected status code: 200
  // Expected behavior: with no players left the game is deleted, with its players and chat
  // Expected output: { msg: "success", game: null, game_deleted: true }
  test('Last player leaves and the game is deleted', async () => {
    // 1. a cancelled game whose only player is the organizer
    const gameId = await addCancelledGame(organizerId);

    // 2. leave it through the interface
    const response = await api.post(`/api/games/${gameId}/leave`).set(asUser(organizerId));

    // 3. check the reply
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', game: null, game_deleted: true });

    // 4. check the game, its players and its chat are really gone
    expect(await getGameById(gameId)).toBeNull();
    expect(await getPlayerIds(gameId)).toEqual([]);
    const chats = await selectRows<{ chat_id: number }>(
      'SELECT chat_id FROM chats WHERE game_id = ?', [gameId]);
    expect(chats).toEqual([]);
  });

  // Input: a player leaves a completed game the organizer is still in
  // Expected status code: 200
  // Expected behavior: the player is taken off it, the game stays for the organizer, and
  //   since the game is over the organizer isn't told
  // Expected output: { msg: "success", game, game_deleted: false }
  test('Player leaves a completed game', async () => {
    const gameId = await addStartedGame(organizerId);
    await addPlayer(gameId, playerId);
    await setGameStatus(gameId, 'completed');

    const response = await api.post(`/api/games/${gameId}/leave`).set(asUser(playerId));

    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ msg: 'success', game_deleted: false, game: { game_id: gameId, status: 'completed' } });
    expect(await getPlayerIds(gameId)).toEqual([organizerId]);
  });

  // Input: Paul leaves a game no game has the id of, Pia leaves a game she isn't in, and the
  //   organizer leaves their upcoming game and their started game
  // Expected status code: 404, then 409 three times
  // Expected behavior: nobody leaves, the organizer must cancel or complete their game first
  // Expected output: { msg: "not_found" }, { msg: "not_player" }, { msg: "organizer_must_cancel" }
  //   and { msg: "organizer_must_complete" }
  test('Leaving is turned down when it is not allowed', async () => {
    const upcomingId = await addGame(organizerId);
    await addPlayer(upcomingId, playerId);
    const startedId = await addStartedGame(organizerId);

    const cases: [number, number, number, string][] = [
      [MISSING_GAME_ID, playerId, 404, 'not_found'],
      [upcomingId, otherPlayerId, 409, 'not_player'],
      [upcomingId, organizerId, 409, 'organizer_must_cancel'],
      [startedId, organizerId, 409, 'organizer_must_complete'],
    ];
    for (const [gameId, userId, status, msg] of cases) {
      const response = await api.post(`/api/games/${gameId}/leave`).set(asUser(userId));
      expect(response.status).toBe(status);
      expect(response.body).toMatchObject({ msg });
    }
    expect(await getPlayerIds(upcomingId)).toEqual(expect.arrayContaining([organizerId, playerId]));
    expect(await getPlayerIds(startedId)).toEqual([organizerId]);
  });
});
