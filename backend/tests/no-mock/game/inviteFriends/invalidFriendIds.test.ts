import { addGame, addUser, asUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: friend_ids missing, empty, or with something that isn't a user id
  // Expected status code: 400
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "invalid_request", error: "friend_ids must be a list of 1 to 100 user ids" }
  test.each([
    ['missing', {}],
    ['empty', { friend_ids: [] }],
    ['text', { friend_ids: ['5'] }],
    ['not whole', { friend_ids: [5.5] }],
    ['zero', { friend_ids: [0] }],
  ])('friend_ids %s is rejected', async (_name, body) => {
    const gameId = await addGame(organizerId);

    const response = await asUser(organizerId).post(`/api/games/${gameId}/invites`)
      .send(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'friend_ids must be a list of 1 to 100 user ids',
    });
  });
});
