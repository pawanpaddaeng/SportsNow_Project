import { selectRows } from '../../../src/core/db/mysql';
import { api, cleanUpAfterTests } from '../helpers';

// a real sign up needs an ID token Google signed for this app, which a test can't get. So these
// tests stop at Google's check or before it, and the mocked tests cover a successful sign up

cleanUpAfterTests();

// how many users are really saved with this email
async function countUsersWithEmail(email: string): Promise<number> {
  const rows = await selectRows<{ total: number }>('SELECT COUNT(*) AS total FROM users WHERE email = ?', [email]);
  return Number(rows[0]?.total ?? 0);
}

// Interface POST /api/auth/signup
describe('Unmocked: POST /api/auth/signup', () => {
  // Input: no body, then id_token and email with no f_name, l_name or birthYear
  // Expected status code: 400
  // Expected behavior: Google is never asked and no user is saved
  // Expected output: { msg: "invalid_request" } with an error naming what is missing
  test('Rejects a sign up with missing fields', async () => {
    const email = 'no-mock-signup-missing@example.com';

    const noBody = await api.post('/api/auth/signup');
    expect(noBody.status).toBe(400);
    expect(noBody.body).toEqual({ msg: 'invalid_request', error: 'id_token and email are required' });

    const noProfile = await api.post('/api/auth/signup').send({ id_token: 'some-token', email });
    expect(noProfile.status).toBe(400);
    expect(noProfile.body).toEqual({
      msg: 'invalid_request',
      error: 'First Name, Last Name and Birth Year fields are compulsory',
    });

    expect(await countUsersWithEmail(email)).toBe(0);
  });

  // Input: every field filled in, with an id_token Google didn't sign
  // Expected status code: 401
  // Expected behavior: Google's check fails, so no user is saved
  // Expected output: { msg: "invalid_token", error: "Invalid Google ID token" }
  test('Rejects an ID token Google did not sign', async () => {
    const email = 'no-mock-signup-fake-token@example.com';

    const response = await api.post('/api/auth/signup').send({
      id_token: 'not-a-real-google-token',
      email,
      f_name: 'Alice',
      l_name: 'Tester',
      birthYear: 1995,
    });

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ msg: 'invalid_token', error: 'Invalid Google ID token' });
    expect(await countUsersWithEmail(email)).toBe(0);
  });
});
