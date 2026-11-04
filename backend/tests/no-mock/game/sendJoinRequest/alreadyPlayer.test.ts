import { addGame, addPlayer, addUser, api, asUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let playerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: a user who is already in the game asks to join it
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "already_player", error: "You're already in this game" }
  test('Already a player', async () => {
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);

    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(playerId))
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'already_player', error: "You're already in this game" });
  });
});
