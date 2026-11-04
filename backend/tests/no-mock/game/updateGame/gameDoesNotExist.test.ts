import { addUser, asUser, cleanUpAfterTests, validGameBody } from '../../helpers';

// far above any id the tests create
const MISSING_GAME_ID = 999999999;

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
    const response = await asUser(userId).put(`/api/games/${MISSING_GAME_ID}`)
      .send(validGameBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'not_found', error: 'This game does not exist' });
  });
});
