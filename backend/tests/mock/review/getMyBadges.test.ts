import request from 'supertest';

import { createApp } from '../../../src/app';
import * as badgesDb from '../../../src/review/db/badges';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/users/me/badges
describe('Mocked: GET /api/users/me/badges', () => {
  // Mocked behavior: getBadges is replaced so only the route runs
  // Input: a signed in player asks for their badges
  // Expected status code: 200
  // Expected behavior: the badges are looked up for the signed in user
  // Expected output: { msg: "success", badges }
  test('Returns the signed in user\'s badges', async () => {
    const player = testPlayer();
    const token = signIn(player);
    const getBadges = jest.spyOn(badgesDb, 'getBadges').mockResolvedValue(['first_game', 'games_10']);

    const response = await api.get('/api/users/me/badges').set(withToken(token));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', badges: ['first_game', 'games_10'] });
    expect(getBadges).toHaveBeenCalledWith(player.userId);
  });

  // Mocked behavior: none, the request is refused before any lookup
  // Input: no sign in
  // Expected status code: 400 or above, requireAuth refuses it
  // Expected behavior: no badges are read
  // Expected output: an error
  test('Needs a signed in user', async () => {
    const getBadges = jest.spyOn(badgesDb, 'getBadges').mockResolvedValue([]);

    const response = await request(createApp()).get('/api/users/me/badges');

    expect(response.status).toBeGreaterThanOrEqual(400);
    expect(getBadges).not.toHaveBeenCalled();
  });

  // Mocked behavior: getBadges throws, as when the database is down
  // Input: a signed in player asks for their badges
  // Expected status code: 500
  // Expected behavior: the error is handled and the server keeps running
  // Expected output: { msg: "server_error", error: "Could not load your badges, try again later" }
  test('Database fails', async () => {
    const token = signIn(testPlayer());
    const getBadges = jest.spyOn(badgesDb, 'getBadges').mockRejectedValue(new Error('Forced error'));

    const response = await api.get('/api/users/me/badges').set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load your badges, try again later' });
    expect(getBadges).toHaveBeenCalledTimes(1);
  });
});
