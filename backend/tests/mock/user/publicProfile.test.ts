import * as friendsDb from '../../../src/friend/db/friends';
import * as badgesDb from '../../../src/review/db/badges';
import * as reputationDb from '../../../src/review/db/reputation';
import * as usersDb from '../../../src/user/db/users';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testOrganizer, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/users/{userId}
describe('Mocked: GET /api/users/{userId}', () => {
  // Mocked behavior: the users table holds the signed in organizer and player 2, badges are
  //   replaced, and getReputation throws as if the database were down
  // Input: the organizer opens player 2's profile
  // Expected status code: 500
  // Expected behavior: the error is logged, no details are sent
  // Expected output: { msg: "server_error" }
  test('Database error', async () => {
    const organizer = testOrganizer();
    const player = testPlayer();
    const token = signIn(organizer);
    jest.spyOn(usersDb, 'findUserById').mockImplementation((userId) =>
      Promise.resolve([organizer, player].find((user) => user.userId === userId) ?? null));
    jest.spyOn(badgesDb, 'getBadges').mockResolvedValue(['first_game']);
    jest.spyOn(friendsDb, 'getFriendshipStatus').mockResolvedValue('none');
    jest.spyOn(reputationDb, 'getReputation').mockRejectedValue(new Error('database is down'));

    const response = await api.get(`/api/users/${player.userId}`).set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body.msg).toBe('server_error');
    expect(response.body.profile).toBeUndefined();
  });
});
