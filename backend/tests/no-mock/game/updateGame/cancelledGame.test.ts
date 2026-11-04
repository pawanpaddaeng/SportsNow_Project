import { getGameById } from '../../../../src/game/db/games';
import {
  addGame,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  setGameStatus,
  validGameBody,
} from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: the organizer edits their cancelled game
  // Expected status code: 409
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "game_cancelled", error: "This game was cancelled" }
  test('Cancelled game', async () => {
    // 1. a cancelled game
    const gameId = await addGame(organizerId, { title: 'Original Title' });
    await setGameStatus(gameId, 'cancelled');

    // 2. edit it through the interface
    const response = await api.put(`/api/games/${gameId}`).set(asUser(organizerId))
      .send(validGameBody());

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'game_cancelled', error: 'This game was cancelled' });
    expect((await getGameById(gameId))?.title).toBe('Original Title');
  });
});
