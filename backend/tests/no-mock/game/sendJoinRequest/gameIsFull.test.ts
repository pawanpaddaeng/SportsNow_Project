import { addGame, addPlayer, addUser, api, asUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let playerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: a user asks to join a game with no spots left
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "game_full", error: "This game is full" }
  test('Game is full', async () => {
    // 1. a game for two players: its organizer and one who joined
    const gameId = await addGame(organizerId, { maxPlayers: 2 });
    await addPlayer(gameId, playerId);

    // 2. ask to join it through the interface
    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'beginner' });

    // 3. check it was rejected
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'game_full', error: 'This game is full' });
  });
});
