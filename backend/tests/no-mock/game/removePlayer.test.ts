import { addGame, addPlayer, addUser, api, asUser, cleanUpAfterTests, getPlayerIds, MISSING_GAME_ID } from '../helpers';
import { type ApiReply, expectGameReply } from '../../sharedHelpers';

let organizerId: number;
let playerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
});

cleanUpAfterTests();

// Interface DELETE /api/games/{gameId}/players/{userId}
describe('Unmocked: DELETE /api/games/{gameId}/players/{userId}', () => {
  // Input: the organizer removes the other player from their upcoming game, which has
  //   the organizer and that player in it
  // Expected status code: 200
  // Expected behavior: the player is no longer in the game, only the organizer is left
  // Expected output: { msg: "success", game } with the game's player_count 1
  test('Organizer removes the second player', async () => {
    // 1. an upcoming game with the organizer and a second player in it
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);

    // 2. remove the player through the interface
    const response = await api.delete(`/api/games/${gameId}/players/${playerId}`).set(asUser(organizerId));

    // 3. check the reply
    expectGameReply(response as ApiReply, { game_id: gameId, player_count: 1 });

    // 4. check only the organizer is left in the game
    expect(await getPlayerIds(gameId)).toEqual([organizerId]);
  });

  // Input: Paul tries to remove the organizer, the organizer removes Paul from a game no game
  //   has the id of, removes themselves, and removes Otto, who isn't in the game
  // Expected status code: 403, 404, 409 and 404
  // Expected behavior: nobody is removed
  // Expected output: { msg: "forbidden" }, { msg: "not_found" }, { msg: "organizer_self" } and
  //   { msg: "player_not_found" }
  test('Removing is turned down when it is not allowed', async () => {
    const outsiderId = await addUser('Otto');
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);

    const cases: [number, number, number, number, string][] = [
      [gameId, playerId, organizerId, 403, 'forbidden'],
      [MISSING_GAME_ID, organizerId, playerId, 404, 'not_found'],
      [gameId, organizerId, organizerId, 409, 'organizer_self'],
      [gameId, organizerId, outsiderId, 404, 'player_not_found'],
    ];
    for (const [targetGameId, userId, removedId, status, msg] of cases) {
      const response = await api.delete(`/api/games/${targetGameId}/players/${removedId}`).set(asUser(userId));
      expect(response.status).toBe(status);
      expect(response.body).toMatchObject({ msg });
    }
    expect(await getPlayerIds(gameId)).toEqual(expect.arrayContaining([organizerId, playerId]));
  });
});
