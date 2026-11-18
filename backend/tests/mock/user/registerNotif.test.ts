import * as deviceTokens from '../../../src/core/notifications/deviceTokens';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface POST /api/register_notif
describe('Mocked: POST /api/register_notif', () => {
  // Mocked behavior: saveDeviceToken is replaced so only the route runs. Sign in is mocked
  //   only so the route can run
  // Input: player 2 sends " device-token " with spaces around it
  // Expected status code: 200
  // Expected behavior: the token is saved for player 2 with the spaces trimmed
  // Expected output: { msg: "success" }
  test('Saves the trimmed token for the signed in user', async () => {
    const player = testPlayer();
    const token = signIn(player);
    const saveDeviceToken = jest.spyOn(deviceTokens, 'saveDeviceToken').mockResolvedValue();

    const response = await api.post('/api/register_notif').set(withToken(token)).send({ fcm_token: ' device-token ' });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(saveDeviceToken).toHaveBeenCalledWith('device-token', player.userId);
  });

  // Mocked behavior: saveDeviceToken throws as if the database were down
  // Input: player 2 sends a device token
  // Expected status code: 500
  // Expected behavior: the error is handled and the server keeps running
  // Expected output: { msg: "server_error", error: "Could not register notifications, try again later" }
  test('Database fails while saving the token', async () => {
    const token = signIn(testPlayer());
    jest.spyOn(deviceTokens, 'saveDeviceToken').mockRejectedValue(new Error('database is down'));

    const response = await api.post('/api/register_notif').set(withToken(token)).send({ fcm_token: 'device-token' });

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not register notifications, try again later' });
  });
});
