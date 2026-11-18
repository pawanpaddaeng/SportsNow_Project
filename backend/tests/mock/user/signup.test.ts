import { verifyAuthToken } from '../../../src/user/auth/jwt';
import * as usersDb from '../../../src/user/db/users';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests } from '../helpers';
import { GOOGLE_ACCOUNT, mockGoogleCheck } from './googleHelpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// a sign up from the Google account the mocked check accepts
const SIGN_UP = {
  id_token: 'google-signed-token',
  email: GOOGLE_ACCOUNT.email,
  f_name: 'Nora',
  l_name: 'Newuser',
  birthYear: 1999,
};

// Interface POST /api/auth/signup
describe('Mocked: POST /api/auth/signup', () => {
  // Mocked behavior: Google's check accepts the ID token. The users database is replaced:
  //   no user has the Google account, and createUser saves the new user as user 7
  // Input: id_token, email, f_name, l_name and birthYear
  // Expected status code: 201
  // Expected behavior: the user is saved with their Google account, email and profile
  // Expected output: { msg: "signedIn", jwt_token } where the JWT signs in user 7
  test('Creates the account and signs the user in', async () => {
    mockGoogleCheck();
    jest.spyOn(usersDb, 'userExists').mockResolvedValue(false);
    const createUser = jest.spyOn(usersDb, 'createUser').mockResolvedValue(7);

    const response = await api.post('/api/auth/signup').send(SIGN_UP);

    expect(response.status).toBe(201);
    expect(response.body).toEqual({ msg: 'signedIn', jwt_token: expect.any(String) });
    expect(createUser).toHaveBeenCalledWith({
      googleId: GOOGLE_ACCOUNT.sub,
      email: GOOGLE_ACCOUNT.email,
      firstName: 'Nora',
      lastName: 'Newuser',
      birthYear: 1999,
    });
    const { jwt_token: jwtToken } = response.body as { jwt_token: string };
    expect(verifyAuthToken(jwtToken)).toMatchObject({ ok: true, payload: { user_id: 7 } });
  });

  // Mocked behavior: Google's check accepts the ID token, and userExists says the Google
  //   account already has a user. createUser is watched
  // Input: a sign up from that Google account
  // Expected status code: 409
  // Expected behavior: no second user is saved, the app tells them to sign in instead
  // Expected output: { msg: "already_exists" }
  test('Google account already has a user', async () => {
    mockGoogleCheck();
    jest.spyOn(usersDb, 'userExists').mockResolvedValue(true);
    const createUser = jest.spyOn(usersDb, 'createUser');

    const response = await api.post('/api/auth/signup').send(SIGN_UP);

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'already_exists' });
    expect(createUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: Google's check accepts the ID token for new.user@example.com.
  //   createUser is watched
  // Input: a sign up whose email is someone.else@example.com
  // Expected status code: 400
  // Expected behavior: no user is saved, the email must belong to the Google account
  // Expected output: { msg: "invalid_request", error: "email does not match the Google account" }
  test('Email does not match the Google account', async () => {
    mockGoogleCheck();
    const createUser = jest.spyOn(usersDb, 'createUser');

    const response = await api.post('/api/auth/signup').send({ ...SIGN_UP, email: 'someone.else@example.com' });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'email does not match the Google account' });
    expect(createUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: Google's check accepts the ID token, userExists says there is no user,
  //   and createUser throws as if the database were down
  // Input: a valid sign up
  // Expected status code: 500
  // Expected behavior: the error is handled and no JWT is sent
  // Expected output: { msg: "server_error", error: "Could not create account, try again later" }
  test('Database fails while saving the user', async () => {
    mockGoogleCheck();
    jest.spyOn(usersDb, 'userExists').mockResolvedValue(false);
    jest.spyOn(usersDb, 'createUser').mockRejectedValue(new Error('database is down'));

    const response = await api.post('/api/auth/signup').send(SIGN_UP);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not create account, try again later' });
  });
});
