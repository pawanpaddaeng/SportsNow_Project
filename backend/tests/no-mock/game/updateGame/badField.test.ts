import { getGameById } from '../../../../src/game/db/games';
import { addGame, addUser, asUser, cleanUpAfterTests, validGameBody } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: the organizer edits their game with sport "chess"
  // Expected status code: 400
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "invalid_request", error } listing the allowed sports
  test('Bad field', async () => {
    // 1. a game to edit
    const gameId = await addGame(organizerId, { title: 'Original Title' });

    // 2. edit it with a sport that isn't allowed through the interface
    const response = await asUser(organizerId).put(`/api/games/${gameId}`)
      .send(validGameBody({ sport: 'chess' }));

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'sport must be one of: basketball, soccer, volleyball, tennis, other',
    });
    const saved = await getGameById(gameId);
    expect(saved?.title).toBe('Original Title');
    expect(saved?.sport).toBe('basketball');
  });
});
