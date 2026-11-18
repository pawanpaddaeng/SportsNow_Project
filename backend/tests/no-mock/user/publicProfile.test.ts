import { ageRange } from '../../../src/user/routes/publicProfileRoutes';
import { addFriends, addUser, api, asUser, cleanUpAfterTests, withToken } from '../helpers';

// a user id far above any id the tests create
const MISSING_USER_ID = 999999999;

let aliceId: number;
let benId: number;

beforeEach(async () => {
  aliceId = await addUser('Alice');
  benId = await addUser('Ben');
});

cleanUpAfterTests();

// Interface GET /api/users/{userId}
describe('Unmocked: GET /api/users/{userId}', () => {
  // Input: no Authorization header, then a token that isn't a real JWT
  // Expected status code: 400, then 401
  // Expected behavior: no profile is read
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    const noToken = await api.get(`/api/users/${benId}`);
    expect(noToken.status).toBe(400);
    expect(noToken.body).toMatchObject({ msg: 'invalid_request' });

    const badToken = await api.get(`/api/users/${benId}`).set(withToken('not-a-real-token'));
    expect(badToken.status).toBe(401);
    expect(badToken.body).toEqual({ msg: 'invalid_token', error: 'Invalid jwt_token' });
  });

  // Input: Alice opens Ben's profile. Ben is new, so he never played, and isn't her friend
  // Expected status code: 200
  // Expected behavior: only Ben's public details are sent, never his email or birth year
  // Expected output: { msg: "success", profile } with his name, age range, 0 games played, no
  //   score or badges, and friendship_status "none"
  test('Returns only the public details of another player', async () => {
    const response = await api.get(`/api/users/${benId}`).set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      msg: 'success',
      profile: {
        user_id: benId,
        first_name: 'Ben',
        last_name: 'Tester',
        age_range: ageRange(1995, new Date().getUTCFullYear()),
        games_played: 0,
        reputation_score: null,
        badges: [],
        friendship_status: 'none',
      },
    });
    const sent = JSON.stringify(response.body);
    expect(sent).not.toContain('no-mock-test@example.com');
    expect(sent).not.toContain('1995');
  });

  // Input: Alice and Ben are friends, and Alice opens Ben's profile
  // Expected status code: 200
  // Expected behavior: the app knows not to offer Add Friend
  // Expected output: profile.friendship_status "friends"
  test('Shows a friend as a friend', async () => {
    await addFriends(aliceId, benId);

    const response = await api.get(`/api/users/${benId}`).set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body.profile).toMatchObject({ user_id: benId, friendship_status: 'friends' });
  });

  // Input: Alice opens her own profile
  // Expected status code: 200
  // Expected behavior: the app knows not to offer Add Friend
  // Expected output: profile.friendship_status "self"
  test('Own profile is marked self', async () => {
    const response = await api.get(`/api/users/${aliceId}`).set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body.profile).toMatchObject({ user_id: aliceId, first_name: 'Alice', friendship_status: 'self' });
  });

  // Input: a user id nobody has
  // Expected status code: 404
  // Expected behavior: nothing is read for the missing user
  // Expected output: { msg: "user_not_found", error: "This player does not exist" }
  test('Player does not exist', async () => {
    const response = await api.get(`/api/users/${MISSING_USER_ID}`).set(asUser(aliceId));

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'user_not_found', error: 'This player does not exist' });
  });

  // Input: a user id of "abc"
  // Expected status code: 400
  // Expected behavior: nothing is read
  // Expected output: { msg: "invalid_request", error: "userId must be a positive whole number" }
  test('Rejects a bad user id', async () => {
    const response = await api.get('/api/users/abc').set(asUser(aliceId));

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'userId must be a positive whole number' });
  });
});
