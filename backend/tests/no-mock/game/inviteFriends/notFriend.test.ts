import { addFriends, addGame, addUser, api, asUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let friendId: number;
let strangerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  friendId = await addUser('Fay');
  strangerId = await addUser('Sam');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: the organizer invites a friend and a user who isn't their friend
  // Expected status code: 403
  // Expected behavior: the whole invite is rejected, not even the friend is invited
  // Expected output: { msg: "not_friend", error: "You can only invite your friends" }
  test('Organizer cannot invite someone who is not their friend', async () => {
    // 1. an upcoming game, and one friend of the organizer
    const gameId = await addGame(organizerId);
    await addFriends(organizerId, friendId);

    // 2. invite the friend and the stranger through the interface
    const response = await api.post(`/api/games/${gameId}/invites`).set(asUser(organizerId))
      .send({ friend_ids: [friendId, strangerId] });

    // 3. check it was rejected
    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'not_friend', error: 'You can only invite your friends' });
  });
});
