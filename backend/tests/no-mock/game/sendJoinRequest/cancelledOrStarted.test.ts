import { addGame, addUser, asUser, cleanUpAfterTests, ONE_DAY_MS, setGameStatus } from '../../helpers';

let organizerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: a user asks to join a cancelled game, and a game that already started
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "game_cancelled" } and { msg: "game_started" }
  test.each([
    ['cancelled', 'game_cancelled', async () => {
      const gameId = await addGame(organizerId);
      await setGameStatus(gameId, 'cancelled');
      return gameId;
    }],
    ['started', 'game_started', () => addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) })],
  ] as const)('Asking to join a %s game', async (_name, msg, setUpGame) => {
    const gameId = await setUpGame();

    const response = await asUser(requesterId).post(`/api/games/${gameId}/join-requests`)
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
  });
});
