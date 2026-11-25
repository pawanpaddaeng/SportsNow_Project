import { findUserById } from '../../../src/user/db/users';
import { addUser, api, asUser, type Call, cleanUpAfterTests, expectSignInRequired } from '../helpers';
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
    await expectSignInRequired((headers) => api.put('/api/users/me').set(headers).send(NEW_PROFILE) as Call);

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

  // Input: no body at all
  // Expected status code: 400
  // Expected behavior: the profile doesn't change
  // Expected output: { msg: "invalid_request", error: "First Name, Last Name and Birth Year fields are compulsory" }
  test('Rejects a call with no profile', async () => {
    const response = await api.put('/api/users/me').set(asUser(aliceId));

    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'First Name, Last Name and Birth Year fields are compulsory',
    });
    await expectSavedProfile(aliceId, ORIGINAL);
  });

  // Input: a profile with one bad value: a first name over 100 characters, a first or last name
  //   with characters that aren't allowed, a birth year for someone under 19, or one that isn't
  //   a whole number
  // Expected status code: 400
  // Expected behavior: the profile doesn't change
  // Expected output: { msg: "invalid_request", error } naming the field and the rule it breaks
  test.each([
    ['a first name that is too long', { f_name: 'A'.repeat(101) }, 'First Name must be at most 100 characters'],
    ['a first name with a digit', { f_name: 'Al1ce' }, 'First Name contains characters that aren\'t allowed: 1'],
    ['a last name with a symbol', { l_name: 'Tester<' }, 'Last Name contains characters that aren\'t allowed: <'],
    ['a birth year for someone under 19', { birthYear: new Date().getFullYear() - 18 }, 'Birth Year must be between'],
    ['a birth year that is not a whole number', { birthYear: 1990.5 }, 'Birth Year must be between'],
  ])('Rejects a profile with %s', async (_case, change, error) => {
    const response = await api.put('/api/users/me').set(asUser(aliceId)).send({ ...NEW_PROFILE, ...change });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: expect.stringContaining(error) as unknown });
    await expectSavedProfile(aliceId, ORIGINAL);
  });

  // Input: the new profile with the birth year sent as text, "1990", and spaces around the names
  // Expected status code: 200
  // Expected behavior: the birth year is saved as the number 1990 and the names without the spaces
  // Expected output: { msg: "success", user } with the new values
  test('Accepts a birth year sent as text and trims the names', async () => {
    const response = await api.put('/api/users/me').set(asUser(aliceId))
      .send({ f_name: '  Alicia ', l_name: 'Newname  ', birthYear: '1990' });

    expect(response.status).toBe(200);
    await expectSavedProfile(aliceId, { firstName: 'Alicia', lastName: 'Newname', birthYear: 1990 });
  });
});
