import * as deviceTokens from '../../../src/core/notifications/deviceTokens';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface POST /api/register_notif
describe('Mocked: POST /api/register_notif', () => {
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
