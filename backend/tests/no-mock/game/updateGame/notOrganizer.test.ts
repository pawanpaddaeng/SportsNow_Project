import { getGameById } from '../../../../src/game/db/games';
import { addGame, addUser, api, asUser, cleanUpAfterTests, validGameBody } from '../../helpers';

let organizerId: number;
let otherUserId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  otherUserId = await addUser('Omar');
});

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: a user who isn't the organizer edits the game
  // Expected status code: 403
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "forbidden", error: "Only the organizer can change this game" }
  test('Not the organizer', async () => {
    const gameId = await addGame(organizerId, { title: 'Original Title' });

    const response = await api.put(`/api/games/${gameId}`).set(asUser(otherUserId))
      .send(validGameBody());

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can change this game' });
    expect((await getGameById(gameId))?.title).toBe('Original Title');
  });
});
