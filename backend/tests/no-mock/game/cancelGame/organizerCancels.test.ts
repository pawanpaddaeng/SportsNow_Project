import { getGameById } from '../../../../src/game/db/games';
import { addGame, addUser, api, asUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/cancel
describe('Unmocked: POST /api/games/{gameId}/cancel', () => {
  // Input: the organizer cancels their upcoming game
  // Expected status code: 200
  // Expected behavior: the game goes from "scheduled" to "cancelled" in the database
  // Expected output: { msg: "success", game } with game.status "cancelled"
  test('Organizer cancels their game', async () => {
    // 1. the organizer's upcoming game
    const gameId = await addGame(organizerId);
    expect((await getGameById(gameId))?.status).toBe('scheduled');

    // 2. cancel it through the interface
    const response = await api.post(`/api/games/${gameId}/cancel`).set(asUser(organizerId));

    // 3. check it was cancelled
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: gameId, status: 'cancelled' });
    expect((await getGameById(gameId))?.status).toBe('cancelled');
  });
});
