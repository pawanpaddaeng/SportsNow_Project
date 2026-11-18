import { addUser, api, asUser, cleanUpAfterTests, expectSignInRequired } from '../helpers';
import { expectedProfile } from './userHelpers';

let aliceId: number;

beforeEach(async () => {
  aliceId = await addUser('Alice');
});

cleanUpAfterTests();

// Interface GET /api/users/me
describe('Unmocked: GET /api/users/me', () => {
  // Input: no Authorization header, then a token that isn't a real JWT
  // Expected status code: 400, then 401
  // Expected behavior: no profile is read
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    await expectSignInRequired((headers) => api.get('/api/users/me').set(headers));
  });

  // Input: Alice asks for her own profile
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: { msg: "success", user } with Alice's id, email, names, birth year and
  //   when her account was created
  test('Returns the signed in user\'s profile', async () => {
    const response = await api.get('/api/users/me').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', user: expectedProfile(aliceId, 'Alice') });
  });
});
