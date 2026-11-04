import { addUser, asUser, cleanUpAfterTests } from '../../helpers';

let userId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
});

cleanUpAfterTests();

// Interface GET /api/games/discover
describe('Unmocked: GET /api/games/discover', () => {
  // Input: a signed in user's request with lng but no lat, or with lat=95&lng=0 (lat can't be over 90)
  // Expected status code: 400
  // Expected behavior: the request is rejected, nothing changes
  // Expected output: { msg: "invalid_request", error: "lat (-90 to 90) and lng (-180 to 180) are required numbers" }
  test.each([
    ['No location', { lng: 0 }],
    ['Location out of range', { lat: 95, lng: 0 }],
  ])('%s', async (_name, location) => {
    const response = await asUser(userId).get('/api/games/discover')
      .query(location);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'lat (-90 to 90) and lng (-180 to 180) are required numbers',
    });
  });
});
