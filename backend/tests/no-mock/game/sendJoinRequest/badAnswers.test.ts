import { addGame, addUser, api, asUser, cleanUpAfterTests, getJoinRequestIds } from '../../helpers';

const TOO_LONG_MESSAGE_LENGTH = 201;

let organizerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: no skill_level, an unknown play_frequency, or a message of 201 characters
  // Expected status code: 400
  // Expected behavior: nothing is saved
  // Expected output: { msg: "invalid_request", error } naming the bad field
  test.each([
    ['no skill_level', {}, 'skill_level must be one of: beginner, intermediate, advanced, competitive'],
    [
      'an unknown play_frequency',
      { skill_level: 'beginner', play_frequency: 'daily' },
      'play_frequency must be one of: rarely, monthly, weekly, several_per_week',
    ],
    [
      'a message of 201 characters',
      { skill_level: 'beginner', message: 'x'.repeat(TOO_LONG_MESSAGE_LENGTH) },
      'message must be text (max 200 characters)',
    ],
  ])('Bad answers: %s', async (_name, body, error) => {
    const gameId = await addGame(organizerId);

    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error });
    expect(await getJoinRequestIds(gameId)).toEqual([]);
  });
});
