import * as mysqlDb from '../../../src/core/db/mysql';
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
  // Mocked behavior: Google's check accepts the ID token. The database query (selectRows) finds
  //   no user with the Google account, so userExists runs for real and says no, and createUser
  //   is replaced to save the new user as user 7
  // Input: id_token, email, f_name, l_name and birthYear
  // Expected status code: 201
  // Expected behavior: the Google account is checked, then the user is saved with their Google
  //   account, email and profile
  // Expected output: { msg: "signedIn", jwt_token } where the JWT signs in user 7
  test('Creates the account and signs the user in', async () => {
    mockGoogleCheck();
    const selectRows = jest.spyOn(mysqlDb, 'selectRows').mockResolvedValue([]);
    const createUser = jest.spyOn(usersDb, 'createUser').mockResolvedValue(7);

    const response = await api.post('/api/auth/signup').send(SIGN_UP);

    expect(response.status).toBe(201);
    expect(response.body).toEqual({ msg: 'signedIn', jwt_token: expect.any(String) });
    expect(selectRows).toHaveBeenCalledWith(expect.stringContaining('WHERE google_id = ?'), [GOOGLE_ACCOUNT.sub]);
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

  // Mocked behavior: Google's check accepts the ID token, and the database query (selectRows)
  //   finds a user with the Google account, so userExists runs for real and says yes.
  //   createUser is watched
  // Input: a sign up from that Google account
  // Expected status code: 409
  // Expected behavior: no second user is saved, the app tells them to sign in instead
  // Expected output: { msg: "already_exists" }
  test('Google account already has a user', async () => {
    mockGoogleCheck();
    jest.spyOn(mysqlDb, 'selectRows').mockResolvedValue([{ found: 1 }]);
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

  // Mocked behavior: Google's check accepts the ID token, but says the account's email isn't
  //   verified. createUser is watched
  // Input: a valid sign up
  // Expected status code: 401
  // Expected behavior: no user is saved, an unverified email can't prove who the user is
  // Expected output: { msg: "invalid_token", error: "Google account has no verified email" }
  test('Google account email is not verified', async () => {
    mockGoogleCheck({ email_verified: false });
    const createUser = jest.spyOn(usersDb, 'createUser');

    const response = await api.post('/api/auth/signup').send(SIGN_UP);

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ msg: 'invalid_token', error: 'Google account has no verified email' });
    expect(createUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: Google's check accepts the ID token but gives no email for the account.
  //   createUser is watched
  // Input: a valid sign up
  // Expected status code: 401
  // Expected behavior: no user is saved, the account's email can't be checked
  // Expected output: { msg: "invalid_token", error: "Google account has no verified email" }
  test('Google account has no email', async () => {
    mockGoogleCheck({ noEmail: true });
    const createUser = jest.spyOn(usersDb, 'createUser');

    const response = await api.post('/api/auth/signup').send(SIGN_UP);

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ msg: 'invalid_token', error: 'Google account has no verified email' });
    expect(createUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: Google's check accepts the ID token but says nothing about the account,
  //   so there is no Google account id. createUser is watched
  // Input: a valid sign up
  // Expected status code: 401
  // Expected behavior: no user is saved, the token is treated as invalid
  // Expected output: { msg: "invalid_token", error: "Invalid Google ID token" }
  test('Google gives no account for the token', async () => {
    mockGoogleCheck(null);
    const createUser = jest.spyOn(usersDb, 'createUser');

    const response = await api.post('/api/auth/signup').send(SIGN_UP);

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ msg: 'invalid_token', error: 'Invalid Google ID token' });
    expect(createUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: Google's check accepts the ID token and the database query finds no user
  //   for it, but createUser throws the database's duplicate entry error, as when two sign ups
  //   for the same Google account arrive at once and the other one was saved first
  // Input: a valid sign up
  // Expected status code: 409
  // Expected behavior: the second sign up is told to sign in instead of failing with a server error
  // Expected output: { msg: "already_exists" }
  test('Same Google account signs up twice at once', async () => {
    mockGoogleCheck();
    jest.spyOn(mysqlDb, 'selectRows').mockResolvedValue([]);
    jest.spyOn(usersDb, 'createUser').mockRejectedValue(Object.assign(new Error('Duplicate entry'), { code: 'ER_DUP_ENTRY' }));

    const response = await api.post('/api/auth/signup').send(SIGN_UP);

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'already_exists' });
  });

  // Mocked behavior: Google's check accepts the ID token, the database query finds no user for
  //   it, and createUser throws as if the database were down
  // Input: a valid sign up
  // Expected status code: 500
  // Expected behavior: the error is handled and no JWT is sent
  // Expected output: { msg: "server_error", error: "Could not create account, try again later" }
  test('Database fails while saving the user', async () => {
    mockGoogleCheck();
    jest.spyOn(mysqlDb, 'selectRows').mockResolvedValue([]);
    jest.spyOn(usersDb, 'createUser').mockRejectedValue(new Error('database is down'));

    const response = await api.post('/api/auth/signup').send(SIGN_UP);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not create account, try again later' });
  });
});
