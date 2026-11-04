import {
  addFriends,
  addGame,
  addPlayer,
  addUser,
  asUser,
  cleanUpAfterTests,
  ONE_DAY_MS,
  setGameStatus,
} from '../../helpers';

let organizerId: number;
let friendId: number;
let playerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  friendId = await addUser('Fay');
  playerId = await addUser('Paul');
  await addFriends(organizerId, friendId);
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: the organizer invites their friend to a game that is cancelled, has started,
  //   is completed, or is full
  // Expected status code: 409
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "game_cancelled" }, { msg: "game_started" } or { msg: "game_full" }
  test.each([
    ['cancelled', 'game_cancelled', async () => {
      const gameId = await addGame(organizerId);
      await setGameStatus(gameId, 'cancelled');
      return gameId;
    }],
    ['started', 'game_started', () => addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) })],
    ['completed', 'game_started', async () => {
      const gameId = await addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) });
      await setGameStatus(gameId, 'completed');
      return gameId;
    }],
    ['full', 'game_full', async () => {
      const gameId = await addGame(organizerId, { maxPlayers: 2 });
      await addPlayer(gameId, playerId);
      return gameId;
    }],
  ] as const)('Organizer cannot invite to a %s game', async (_name, msg, setUpGame) => {
    const gameId = await setUpGame();

    const response = await asUser(organizerId).post(`/api/games/${gameId}/invites`)
      .send({ friend_ids: [friendId] });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
  });
});
