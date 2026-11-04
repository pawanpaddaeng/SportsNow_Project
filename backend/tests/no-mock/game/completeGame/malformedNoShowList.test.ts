import { getGameById } from '../../../../src/game/db/games';
import { addGame, addUser, asUser, cleanUpAfterTests, ONE_DAY_MS } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/complete
describe('Unmocked: POST /api/games/{gameId}/complete', () => {
  // Input: the organizer completes their started game with a no-show id that isn't a user id
  // Expected status code: 400
  // Expected behavior: the game is left as it was
  // Expected output: { msg: "invalid_request", error: "no_show_user_ids must be a list of user ids" }
  test('A malformed no-show list is rejected', async () => {
    // 1. the organizer's game that started yesterday
    const gameId = await addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS) });

    // 2. complete it with a malformed list through the interface
    const response = await asUser(organizerId).post(`/api/games/${gameId}/complete`)
      .send({ no_show_user_ids: ['seven'] });

    // 3. check it was rejected and the game didn't change
    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'no_show_user_ids must be a list of user ids',
    });
    expect((await getGameById(gameId))?.status).toBe('scheduled');
  });
});
