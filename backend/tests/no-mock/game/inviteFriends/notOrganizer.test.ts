import { addFriends, addGame, addPlayer, addUser, api, asUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let playerId: number;
let friendId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  friendId = await addUser('Fay');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: a player who isn't the organizer invites their friend
  // Expected status code: 403
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "forbidden", error: "Only the organizer can invite friends to this game" }
  test('A player who is not the organizer cannot invite', async () => {
    // 1. an upcoming game the player joined, and the player's friend
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);
    await addFriends(playerId, friendId);

    // 2. invite through the interface
    const response = await api.post(`/api/games/${gameId}/invites`).set(asUser(playerId))
      .send({ friend_ids: [friendId] });

    // 3. check it was rejected
    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can invite friends to this game' });
  });
});
