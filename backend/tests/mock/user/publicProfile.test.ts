import * as friendsDb from '../../../src/friend/db/friends';
import * as reputationDb from '../../../src/review/db/reputation';
import * as usersDb from '../../../src/user/db/users';
import { type User } from '../../../src/user/db/users';
import { ageRange } from '../../../src/user/routes/publicProfileRoutes';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testOrganizer, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

/*  replaces the users table with the given users and reputation with 4 games played and no
    ratings. signIn already mocked findUserById, so this replaces it with a lookup by id
 */
function mockUsers(users: User[]) {
  jest.spyOn(usersDb, 'findUserById').mockImplementation((userId) =>
    Promise.resolve(users.find((user) => user.userId === userId) ?? null));
  return jest.spyOn(reputationDb, 'getReputation').mockResolvedValue({ score: 4.2, badges: [], gamesPlayed: 4 });
}

// Interface GET /api/users/{userId}
describe('Mocked: GET /api/users/{userId}', () => {
  // Mocked behavior: the users table holds the organizer (signed in) and player 2, who have
  //   a pending friend request from the organizer to the player. Reputation is 4 games played
  // Input: the organizer opens player 2's profile
  // Expected status code: 200
  // Expected behavior: only public details are sent, never the email or birth year
  // Expected output: { msg: "success", profile } with exactly the public fields
  test('Returns only the public details of another player', async () => {
    const organizer = testOrganizer();
    const player = testPlayer();
    const token = signIn(organizer);
    mockUsers([organizer, player]);
    const status = jest.spyOn(friendsDb, 'getFriendshipStatus').mockResolvedValue('request_sent');

    const response = await api.get(`/api/users/${player.userId}`).set(withToken(token));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      msg: 'success',
      profile: {
        user_id: player.userId,
        first_name: 'Paul',
        last_name: 'Player',
        age_range: ageRange(player.birthYear, new Date().getUTCFullYear()),
        games_played: 4,
        reputation_score: 4.2,
        badges: [],
        friendship_status: 'request_sent',
      },
    });
    const sent = JSON.stringify(response.body);
    expect(sent).not.toContain(player.email);
    expect(sent).not.toContain(String(player.birthYear));
    expect(status).toHaveBeenCalledWith(organizer.userId, player.userId);
  });

  // Mocked behavior: as above
  // Input: the organizer opens their own profile
  // Expected status code: 200
  // Expected behavior: no friendship lookup, the app hides Add Friend
  // Expected output: profile.friendship_status "self"
  test('Own profile is marked self', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    mockUsers([organizer]);
    const status = jest.spyOn(friendsDb, 'getFriendshipStatus');

    const response = await api.get(`/api/users/${organizer.userId}`).set(withToken(token));

    expect(response.status).toBe(200);
    expect(response.body.profile).toMatchObject({ user_id: organizer.userId, friendship_status: 'self' });
    expect(status).not.toHaveBeenCalled();
  });

  // Mocked behavior: the users table holds only the signed in organizer
  // Input: a user id nobody has
  // Expected status code: 404
  // Expected behavior: nothing is looked up for the missing user
  // Expected output: { msg: "user_not_found" }
  test('Unknown player', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    const reputation = mockUsers([organizer]);

    const response = await api.get('/api/users/999').set(withToken(token));

    expect(response.status).toBe(404);
    expect(response.body.msg).toBe('user_not_found');
    expect(reputation).not.toHaveBeenCalled();
  });

  // Mocked behavior: as above
  // Input: an id that isn't a positive whole number
  // Expected status code: 400
  // Expected behavior: nothing is looked up
  // Expected output: { msg: "invalid_request" }
  test.each(['0', 'abc', '1.5'])('Invalid id %s is rejected', async (userId) => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    const reputation = mockUsers([organizer]);

    const response = await api.get(`/api/users/${userId}`).set(withToken(token));

    expect(response.status).toBe(400);
    expect(response.body.msg).toBe('invalid_request');
    expect(reputation).not.toHaveBeenCalled();
  });

  // Mocked behavior: getReputation throws as if the database were down
  // Input: the organizer opens player 2's profile
  // Expected status code: 500
  // Expected behavior: the error is logged, no details are sent
  // Expected output: { msg: "server_error" }
  test('Database error', async () => {
    const organizer = testOrganizer();
    const player = testPlayer();
    const token = signIn(organizer);
    mockUsers([organizer, player]);
    jest.spyOn(friendsDb, 'getFriendshipStatus').mockResolvedValue('none');
    jest.spyOn(reputationDb, 'getReputation').mockRejectedValue(new Error('database is down'));

    const response = await api.get(`/api/users/${player.userId}`).set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body.msg).toBe('server_error');
    expect(response.body.profile).toBeUndefined();
  });

  // Input: birth years for someone turning 19, 24, 25, 34, 64 and 65 in 2026
  // Expected output: the age range shown instead of the birth year
  test.each([
    [2007, '19–24'],
    [2002, '19–24'],
    [2001, '25–34'],
    [1992, '25–34'],
    [1962, '55–64'],
    [1961, '65+'],
  ])('Born in %i shows %s', (birthYear, expected) => {
    expect(ageRange(birthYear, 2026)).toBe(expected);
  });
});