import { addUser, api, asUser, cleanUpAfterTests, MISSING_GAME_ID } from '../../helpers';

let userId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: a signed in user asks to join an id with no game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "not_found", error: "This game does not exist" }
  test("Game doesn't exist", async () => {
    const response = await api.post(`/api/games/${MISSING_GAME_ID}/join-requests`).set(asUser(userId))
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'not_found', error: 'This game does not exist' });
  });
});
