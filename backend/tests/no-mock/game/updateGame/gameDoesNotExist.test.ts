import { addUser, api, asUser, cleanUpAfterTests, MISSING_GAME_ID, validGameBody } from '../../helpers';

let userId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
});

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: a signed in user edits an id with no game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "not_found", error: "This game does not exist" }
  test("Game doesn't exist", async () => {
    const response = await api.put(`/api/games/${MISSING_GAME_ID}`).set(asUser(userId))
      .send(validGameBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'not_found', error: 'This game does not exist' });
  });
});
