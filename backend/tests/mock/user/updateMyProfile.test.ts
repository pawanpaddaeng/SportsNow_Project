import * as usersDb from '../../../src/user/db/users';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

const NEW_PROFILE = { f_name: 'Pablo', l_name: 'Newname', birthYear: 1990 };

// Interface PUT /api/users/me
describe('Mocked: PUT /api/users/me', () => {
  // Mocked behavior: updateUserProfile is replaced. findUserById returns player 2 for the sign
  //   in check, then player 2 with the new values when the route reads them back
  // Input: player 2 changes their first name, last name and birth year
  // Expected status code: 200
  // Expected behavior: updateUserProfile gets player 2's id and the new values
  // Expected output: { msg: "success", user } with the new values
  test('Saves the new profile for the signed in user', async () => {
    const player = testPlayer();
    const token = signIn(player);
    const updated = { ...player, firstName: 'Pablo', lastName: 'Newname', birthYear: 1990 };
    jest.spyOn(usersDb, 'findUserById').mockResolvedValueOnce(player).mockResolvedValueOnce(updated);
    const updateUserProfile = jest.spyOn(usersDb, 'updateUserProfile').mockResolvedValue();

    const response = await api.put('/api/users/me').set(withToken(token)).send(NEW_PROFILE);

    expect(response.status).toBe(200);
    expect(response.body.user).toMatchObject({ user_id: player.userId, f_name: 'Pablo', l_name: 'Newname', birthYear: 1990 });
    expect(updateUserProfile).toHaveBeenCalledWith(player.userId, { firstName: 'Pablo', lastName: 'Newname', birthYear: 1990 });
  });

  // Mocked behavior: updateUserProfile is watched. Sign in is mocked only so the route can run
  // Input: a profile without l_name
  // Expected status code: 400
  // Expected behavior: updateUserProfile is never called
  // Expected output: { msg: "invalid_request" }
  test('Invalid profile never reaches the database', async () => {
    const token = signIn(testPlayer());
    const updateUserProfile = jest.spyOn(usersDb, 'updateUserProfile');

    const response = await api.put('/api/users/me').set(withToken(token)).send({ f_name: 'Pablo', birthYear: 1990 });

    expect(response.status).toBe(400);
    expect(response.body.msg).toBe('invalid_request');
    expect(updateUserProfile).not.toHaveBeenCalled();
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
