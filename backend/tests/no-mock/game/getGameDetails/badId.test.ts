import { addUser, api, asUser, cleanUpAfterTests } from '../../helpers';

let userId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
});

cleanUpAfterTests();

// Interface GET /api/games/{gameId}
describe('Unmocked: GET /api/games/{gameId}', () => {
  // Input: a signed in user opens /api/games/abc
  // Expected status code: 400
  // Expected behavior: the request is rejected, nothing changes
  // Expected output: { msg: "invalid_request", error: "gameId must be a positive whole number" }
  test('Bad id', async () => {
    const response = await api.get('/api/games/abc').set(asUser(userId));

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'gameId must be a positive whole number' });
  });
});
