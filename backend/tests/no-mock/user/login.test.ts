import { api, cleanUpAfterTests } from '../helpers';

// a real login needs an ID token Google signed for this app, which a test can't get. So these
// tests stop at Google's check or before it, and the mocked tests cover a successful login

cleanUpAfterTests();

// Interface POST /api/auth/login
describe('Unmocked: POST /api/auth/login', () => {
  // Input: no body, then an empty id_token
  // Expected status code: 400
  // Expected behavior: Google is never asked and no JWT is made
  // Expected output: { msg: "invalid_request", error: "id_token is required" }
  test('Rejects a login with no ID token', async () => {
    for (const body of [undefined, { id_token: '' }]) {
      const response = await api.post('/api/auth/login').send(body);

      expect(response.status).toBe(400);
      expect(response.body).toEqual({ msg: 'invalid_request', error: 'id_token is required' });
    }
  });

  // Input: an id_token Google didn't sign
  // Expected status code: 401
  // Expected behavior: Google's check fails, so no JWT is made
  // Expected output: { msg: "invalid_token", error: "Invalid Google ID token" }
  test('Rejects an ID token Google did not sign', async () => {
    const response = await api.post('/api/auth/login').send({ id_token: 'not-a-real-google-token' });

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ msg: 'invalid_token', error: 'Invalid Google ID token' });
    expect(response.body.jwt_token).toBeUndefined();
  });
});
