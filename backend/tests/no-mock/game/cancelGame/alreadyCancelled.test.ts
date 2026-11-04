import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, api, cleanUpAfterTests, withToken } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/cancel
describe('Unmocked: POST /api/games/{gameId}/cancel', () => {
  // Input: the organizer cancels the same game twice
  // Expected status code: 200 the first time, then 409
  // Expected behavior: the second cancel is rejected
  // Expected output: { msg: "game_cancelled", error: "This game was cancelled" } the second time
  test('Already cancelled', async () => {
    // 1. the organizer's upcoming game
    const gameId = await addGame(organizerId);
    const token = createAuthToken(organizerId);

    // 2. cancel it once through the interface
    const first = await api.post(`/api/games/${gameId}/cancel`).set(withToken(token));
    expect(first.status).toBe(200);

    // 3. cancel it again and check that's rejected
    const second = await api.post(`/api/games/${gameId}/cancel`).set(withToken(token));
    expect(second.status).toBe(409);
    expect(second.body).toEqual({ msg: 'game_cancelled', error: 'This game was cancelled' });
  });
});
