import { findUserById } from '../../../src/user/db/users';
import { addUser, api, asUser, cleanUpAfterTests, expectSignInRequired } from '../helpers';
import { expectedProfile } from './userHelpers';

let aliceId: number;

beforeEach(async () => {
  aliceId = await addUser('Alice');
});

cleanUpAfterTests();

// checks the names and birth year really saved for the user
async function expectSavedProfile(userId: number, profile: { firstName: string; lastName: string; birthYear: number }) {
  expect(await findUserById(userId)).toMatchObject(profile);
}

const ORIGINAL = { firstName: 'Alice', lastName: 'Tester', birthYear: 1995 };

const NEW_PROFILE = { f_name: 'Alicia', l_name: 'Newname', birthYear: 1990 };

// Interface PUT /api/users/me
describe('Unmocked: PUT /api/users/me', () => {
  // Input: no Authorization header, then a token that isn't a real JWT
  // Expected status code: 400, then 401
  // Expected behavior: the profile doesn't change
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    await expectSignInRequired('put', '/api/users/me', NEW_PROFILE);

    await expectSavedProfile(aliceId, ORIGINAL);
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
      user: expectedProfile(aliceId, 'Alicia', { l_name: 'Newname', birthYear: 1990 }),
    });
    await expectSavedProfile(aliceId, { firstName: 'Alicia', lastName: 'Newname', birthYear: 1990 });
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
    await expectSavedProfile(aliceId, ORIGINAL);
  });
});
