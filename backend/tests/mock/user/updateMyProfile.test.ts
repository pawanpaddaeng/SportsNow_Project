import * as usersDb from '../../../src/user/db/users';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

const NEW_PROFILE = { f_name: 'Pablo', l_name: 'Newname', birthYear: 1990 };

// Interface PUT /api/users/me
describe('Mocked: PUT /api/users/me', () => {
  // Mocked behavior: findUserById returns player 2 for the sign in check, then no one when the
  //   route reads the profile back, as if the account were deleted in between.
  //   updateUserProfile is replaced
  // Input: a valid profile
  // Expected status code: 404
  // Expected behavior: the app is told the account is gone, instead of getting an empty profile
  // Expected output: { msg: "no_user", error: "No account found, sign up or sign in again" }
  test('Account is deleted while the profile is being saved', async () => {
    const player = testPlayer();
    const token = signIn(player);
    jest.spyOn(usersDb, 'findUserById').mockResolvedValueOnce(player).mockResolvedValueOnce(null);
    jest.spyOn(usersDb, 'updateUserProfile').mockResolvedValue();

    const response = await api.put('/api/users/me').set(withToken(token)).send(NEW_PROFILE);

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'no_user', error: 'No account found, sign up or sign in again' });
  });

  // Mocked behavior: updateUserProfile throws as if the database were down
  // Input: a valid profile
  // Expected status code: 500
  // Expected behavior: the error is handled and the server keeps running
  // Expected output: { msg: "server_error", error: "Could not update profile, try again later" }
  test('Database fails while saving the profile', async () => {
    const token = signIn(testPlayer());
    jest.spyOn(usersDb, 'updateUserProfile').mockRejectedValue(new Error('database is down'));

    const response = await api.put('/api/users/me').set(withToken(token)).send(NEW_PROFILE);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not update profile, try again later' });
  });
});
