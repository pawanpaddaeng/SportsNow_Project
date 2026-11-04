import { addGame, addUser, api, asUser, cleanUpAfterTests, getJoinRequestIds } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: the organizer asks to join their own game
  // Expected status code: 409
  // Expected behavior: nothing is saved
  // Expected output: { msg: "own_game", error: "You organize this game" }
  test('Own game', async () => {
    const gameId = await addGame(organizerId);

    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(organizerId))
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'own_game', error: 'You organize this game' });
    expect(await getJoinRequestIds(gameId)).toEqual([]);
  });
});
