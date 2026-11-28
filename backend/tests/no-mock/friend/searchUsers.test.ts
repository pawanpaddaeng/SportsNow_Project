import { executeWrite } from '../../../src/core/db/mysql';
import { addFriends, addUser, api, asUser, cleanUpAfterTests, withToken } from '../helpers';

/*  Test files share one database and search looks at every user, so the names here are
    made up words no other file uses. Each search only checks the users this file made
 */

// how many users this file has saved, so two people can share a name
let peopleAdded = 0;

// saves a real user with this first and last name
async function addPerson(firstName: string, lastName: string): Promise<number> {
  peopleAdded += 1;
  const userId = await addUser(`person${String(peopleAdded)}`);
  await executeWrite('UPDATE users SET first_name = ?, last_name = ? WHERE user_id = ?', [firstName, lastName, userId]);
  return userId;
}

interface FoundUser {
  user_id: number;
  first_name: string;
  last_name: string;
  friendship_status: string;
}

// the ids in the search results that are one of ours, in the order they came back
function idsAmong(users: FoundUser[], ours: number[]): number[] {
  return users.map((user) => user.user_id).filter((id) => ours.includes(id));
}

let searcherId: number;

beforeEach(async () => {
  searcherId = await addPerson('Brimwold', 'Searcher');
});

cleanUpAfterTests();

// Interface GET /api/users/search
describe('Unmocked: GET /api/users/search', () => {
  // Input: no jwt_token, and a jwt_token that isn't a real token
  // Expected status code: 400 with no token, 401 with a bad token
  // Expected behavior: nobody is searched for
  // Expected output: { msg: "invalid_request" } and { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    const noToken = await api.get('/api/users/search').query({ q: 'Brimwold' });
    expect(noToken.status).toBe(400);
    expect(noToken.body).toMatchObject({ msg: 'invalid_request' });

    const badToken = await api.get('/api/users/search').query({ q: 'Brimwold' }).set(withToken('not-a-real-token'));
    expect(badToken.status).toBe(401);
    expect(badToken.body).toEqual({ msg: 'invalid_token', error: 'Invalid jwt_token' });
  });

  // Input: q missing, empty, only spaces, 101 characters, or given twice (an array)
  // Expected status code: 400
  // Expected behavior: nobody is searched for
  // Expected output: { msg: "invalid_request", error: "q must be text of 1 to 100 characters" }
  test.each([
    ['q missing', ''],
    ['q empty', '?q='],
    ['q only spaces', '?q=%20%20%20'],
    ['q of 101 characters', `?q=${'a'.repeat(101)}`],
    ['q given twice', '?q=Brimwold&q=Searcher'],
  ])('Bad query: %s', async (_name, query) => {
    const response = await api.get(`/api/users/search${query}`).set(asUser(searcherId));

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'q must be text of 1 to 100 characters' });
  });

  // Input: q of exactly 100 characters, which matches no one
  // Expected status code: 200
  // Expected behavior: the longest allowed text is accepted
  // Expected output: { msg: "success", users: [] }
  test('Accepts a query of exactly 100 characters', async () => {
    const response = await api.get('/api/users/search').query({ q: 'q'.repeat(100) }).set(asUser(searcherId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', users: [] });
  });

  // Input: q is text no user's name is close to
  // Expected status code: 200
  // Expected behavior: nobody matches
  // Expected output: { msg: "success", users: [] }
  test('No user matches', async () => {
    const response = await api.get('/api/users/search').query({ q: 'Qxjzvwpkfh' }).set(asUser(searcherId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', users: [] });
  });

  // Input: q is only an accent mark (U+0301, the combining acute accent), not a letter
  // Expected status code: 200
  // Expected behavior: accents are removed before names are compared, so nothing is left to
  //   search for and nobody matches
  // Expected output: { msg: "success", users: [] }
  test('A query of only an accent mark matches nobody', async () => {
    const response = await api.get('/api/users/search').query({ q: '́' }).set(asUser(searcherId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', users: [] });
  });

  // Input: the searcher searches their own full name, "Brimwold Searcher", with spaces around it
  // Expected status code: 200
  // Expected behavior: the text is trimmed, and the searcher never finds themselves
  // Expected output: users holds the other "Brimwold Searcher", not the searcher
  test('The searcher is left out of their own results', async () => {
    const twinId = await addPerson('Brimwold', 'Searcher');

    const response = await api.get('/api/users/search').query({ q: '  Brimwold Searcher  ' }).set(asUser(searcherId));

    expect(response.status).toBe(200);
    const ids = (response.body.users as FoundUser[]).map((user) => user.user_id);
    expect(ids).toContain(twinId);
    expect(ids).not.toContain(searcherId);
  });

  // Input: q "Grelvanto", matching a friend, a user the searcher asked, a user who asked the
  //   searcher, a user whose request the searcher declined, and a stranger
  // Expected status code: 200
  // Expected behavior: each result says how that user is connected to the searcher, and an
  //   answered request no longer counts
  // Expected output: each user with user_id, first_name, last_name and friendship_status
  //   friends / request_sent / request_received / none / none
  test('Each result shows its friendship status', async () => {
    const friendId = await addPerson('Grelvanto', 'Friend');
    const askedId = await addPerson('Grelvanto', 'Asked');
    const askerId = await addPerson('Grelvanto', 'Asker');
    const declinedId = await addPerson('Grelvanto', 'Declined');
    const strangerId = await addPerson('Grelvanto', 'Stranger');

    await addFriends(searcherId, friendId);
    await api.post('/api/friends/requests').set(asUser(searcherId)).send({ user_id: askedId }).expect(201);
    await api.post('/api/friends/requests').set(asUser(askerId)).send({ user_id: searcherId }).expect(201);
    const declined = await api.post('/api/friends/requests').set(asUser(declinedId))
      .send({ user_id: searcherId }).expect(201);
    await api.patch(`/api/friends/requests/${String(declined.body.request.request_id)}`)
      .set(asUser(searcherId)).send({ decision: 'decline' }).expect(200);

    const response = await api.get('/api/users/search').query({ q: 'Grelvanto' }).set(asUser(searcherId));

    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    const users = response.body.users as FoundUser[];
    const statusOf = (id: number) => users.find((user) => user.user_id === id);
    expect(statusOf(friendId)).toEqual(
      { user_id: friendId, first_name: 'Grelvanto', last_name: 'Friend', friendship_status: 'friends' });
    expect(statusOf(askedId)?.friendship_status).toBe('request_sent');
    expect(statusOf(askerId)?.friendship_status).toBe('request_received');
    expect(statusOf(declinedId)?.friendship_status).toBe('none');
    expect(statusOf(strangerId)?.friendship_status).toBe('none');
  });

  // Input: q "Thornquell", matching users who
  //   have exactly that name ("Thornquell" with no other words would need an empty last name,
  //   so the query is the full name "Thornquell Ash" in a second search),
  //   have a name starting with it, have a later word starting with it, contain it,
  //   or are only similar (one or two letters off)
  // Expected status code: 200
  // Expected behavior: best match first: exact name, then name starts with it, then a word
  //   starts with it, then contains it, then similar names with the closest first. Matches
  //   that are equally good are in name order, then by id. Names too different are left out
  // Expected output: users in that order
  test('Best matches come first', async () => {
    const startsZed = await addPerson('Thornquell', 'Zed');
    const startsAsh = await addPerson('Thornquell', 'Ash');
    const startsAshTwin = await addPerson('Thornquell', 'Ash');
    const wordStarts = await addPerson('Ada', 'Thornquellson');
    const contains = await addPerson('Ada', 'Bythornquell');
    const oneLetterOff = await addPerson('Thornqwell', 'Ada');
    const twoLettersOff = await addPerson('Thurnqwell', 'Ada');
    const tooDifferent = await addPerson('Plimsby', 'Ada');
    const ours = [startsZed, startsAsh, startsAshTwin, wordStarts, contains, oneLetterOff, twoLettersOff, tooDifferent];

    const response = await api.get('/api/users/search').query({ q: 'thornquell' }).set(asUser(searcherId));

    expect(response.status).toBe(200);
    expect(idsAmong(response.body.users as FoundUser[], ours)).toEqual(
      [startsAsh, startsAshTwin, startsZed, wordStarts, contains, oneLetterOff, twoLettersOff]);

    // the exact full name comes before names that only start with it
    const exact = await api.get('/api/users/search').query({ q: 'Thornquell Ash' }).set(asUser(searcherId));
    expect(exact.status).toBe(200);
    expect(idsAmong(exact.body.users as FoundUser[], [startsAsh, startsAshTwin, startsZed])[0]).toBe(startsAsh);
  });

  // Input: q "Vantrobel", matching 21 users "Vantrobel A" to "Vantrobel U"
  // Expected status code: 200
  // Expected behavior: at most 20 users are returned, the last in name order is cut
  // Expected output: 20 users, "Vantrobel A" to "Vantrobel T"
  test('Returns at most 20 users', async () => {
    const letters = 'ABCDEFGHIJKLMNOPQRSTU'.split('');
    const ids: number[] = [];
    for (const letter of letters) {
      ids.push(await addPerson('Vantrobel', letter));
    }

    const response = await api.get('/api/users/search').query({ q: 'Vantrobel' }).set(asUser(searcherId));

    expect(response.status).toBe(200);
    const users = response.body.users as FoundUser[];
    expect(users).toHaveLength(20);
    expect(users.map((user) => user.last_name)).toEqual(letters.slice(0, 20));
    expect(users.map((user) => user.user_id)).not.toContain(ids[20]);
  });
});
