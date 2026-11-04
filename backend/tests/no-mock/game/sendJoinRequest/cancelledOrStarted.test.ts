import { addCancelledGame, addStartedGame, addUser, api, asUser, cleanUpAfterTests } from '../../helpers';

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
    ['cancelled', 'game_cancelled', () => addCancelledGame(organizerId)],
    ['started', 'game_started', () => addStartedGame(organizerId)],
  ] as const)('Asking to join a %s game', async (_name, msg, setUpGame) => {
    const gameId = await setUpGame();

    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
  });
});
