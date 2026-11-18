import { findUserById } from '../../../src/user/db/users';
import { addUser, api, asUser, cleanUpAfterTests, withToken } from '../helpers';

let aliceId: number;

beforeEach(async () => {
  aliceId = await addUser('Alice');
});

cleanUpAfterTests();

// Alice's names and birth year as really saved
async function getSavedProfile(userId: number) {
  const user = await findUserById(userId);
  return { firstName: user?.firstName, lastName: user?.lastName, birthYear: user?.birthYear };
}

const NEW_PROFILE = { f_name: 'Alicia', l_name: 'Newname', birthYear: 1990 };

// Interface PUT /api/users/me
describe('Unmocked: PUT /api/users/me', () => {
  // Input: no Authorization header, then a token that isn't a real JWT
  // Expected status code: 400, then 401
  // Expected behavior: the profile doesn't change
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    const noToken = await api.put('/api/users/me').send(NEW_PROFILE);
    expect(noToken.status).toBe(400);
    expect(noToken.body).toMatchObject({ msg: 'invalid_request' });

    const badToken = await api.put('/api/users/me').set(withToken('not-a-real-token')).send(NEW_PROFILE);
    expect(badToken.status).toBe(401);
    expect(badToken.body).toEqual({ msg: 'invalid_token', error: 'Invalid jwt_token' });

    expect(await getSavedProfile(aliceId)).toEqual({ firstName: 'Alice', lastName: 'Tester', birthYear: 1995 });
  });

  // Input: Alice changes her first name, last name and birth year
  // Expected status code: 200
  // Expected behavior: the new values are saved
  // Expected output: { msg: "success", user } with the new values
  test('Updates the signed in user\'s profile', async () => {
    const response = await api.put('/api/users/me').set(asUser(aliceId)).send(NEW_PROFILE);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      msg: 'success',
      user: {
        user_id: aliceId,
        email: 'no-mock-test@example.com',
        f_name: 'Alicia',
        l_name: 'Newname',
        birthYear: 1990,
        created_at: expect.any(String),
      },
    });
    expect(await getSavedProfile(aliceId)).toEqual({ firstName: 'Alicia', lastName: 'Newname', birthYear: 1990 });
  });

  // Input: f_name and birthYear, with no l_name
  // Expected status code: 400
  // Expected behavior: the profile doesn't change
  // Expected output: { msg: "invalid_request", error: "First Name, Last Name and Birth Year fields are compulsory" }
  test('Rejects a profile with a missing field', async () => {
    const response = await api.put('/api/users/me').set(asUser(aliceId)).send({ f_name: 'Alicia', birthYear: 1990 });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'First Name, Last Name and Birth Year fields are compulsory',
    });
    expect(await getSavedProfile(aliceId)).toEqual({ firstName: 'Alice', lastName: 'Tester', birthYear: 1995 });
  });
});
