import { executeWrite } from '../../../src/core/db/mysql';
import { addFriends, addGame, addPlayer, addUser, api, asUser, type Call, cleanUpAfterTests, expectSignInRequired } from '../helpers';
import { addCompletedGame, addRating } from './reviewHelpers';

let organizerId: number;
let aliceId: number;
let benId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  aliceId = await addUser('Alice');
  benId = await addUser('Ben');
});

cleanUpAfterTests();

// Interface GET /api/users/me/badges
describe('Unmocked: GET /api/users/me/badges', () => {
  // Input: no Authorization header, then a token that isn't a real JWT
  // Expected status code: 400, then 401
  // Expected behavior: no badges are read or saved
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    await expectSignInRequired((headers) => api.get('/api/users/me/badges').set(headers) as Call);
  });

  // Input: a new user who hasn't done anything asks for their badges
  // Expected status code: 200
  // Expected behavior: nothing is saved
  // Expected output: { msg: "success", badges: [] }
  test('A new player has no badges', async () => {
    const response = await api.get('/api/users/me/badges').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', badges: [] });
  });

  // Input: Alice hosts a game, Ben is a player in it, they're friends, and after a completed
  //   game of Olivia's Ben rated Alice
  // Expected status code: 200
  // Expected behavior: each gets the getting started badges for what they did
  // Expected output: Alice has first_game (Olivia's game), first_host and first_friend. Ben has
  //   first_game, first_friend and first_rating
  test('Earns the getting started badges', async () => {
    const aliceGameId = await addGame(aliceId);
    await addPlayer(aliceGameId, benId);
    await addFriends(aliceId, benId);
    const completedGameId = await addCompletedGame(organizerId, [aliceId, benId]);
    await addRating(completedGameId, benId, aliceId, 4);

    const alice = await api.get('/api/users/me/badges').set(asUser(aliceId));
    const ben = await api.get('/api/users/me/badges').set(asUser(benId));

    expect(alice.status).toBe(200);
    expect(alice.body).toEqual({ msg: 'success', badges: ['first_game', 'first_host', 'first_friend'] });
    expect(ben.status).toBe(200);
    expect(ben.body).toEqual({ msg: 'success', badges: ['first_game', 'first_friend', 'first_rating'] });
  });

  // Input: Alice and Ben are friends and Alice reads her badges, then they stop being friends
  // Expected status code: 200
  // Expected behavior: the badge was saved the first time, so it isn't taken away
  // Expected output: { msg: "success", badges: ["first_friend"] } both times
  test('A badge stays after the player no longer qualifies for it', async () => {
    await addFriends(aliceId, benId);
    const before = await api.get('/api/users/me/badges').set(asUser(aliceId));
    await executeWrite('DELETE FROM friendships WHERE user_id IN (?, ?)', [aliceId, benId]);

    const after = await api.get('/api/users/me/badges').set(asUser(aliceId));

    expect(before.body).toEqual({ msg: 'success', badges: ['first_friend'] });
    expect(after.status).toBe(200);
    expect(after.body).toEqual({ msg: 'success', badges: ['first_friend'] });
  });

  // Input: Alice and Ben played 10 completed games of Olivia's, and Ben gave Alice 6 stars in
  //   every category in each
  // Expected status code: 200
  // Expected behavior: 10 games played earns Regular, and 10 perfect ratings with a score
  //   above 5 earns MVP and Mega MVP
  // Expected output: { msg: "success", badges: ["first_game", "games_10", "mvp", "mega_mvp"] }
  test('Ten perfect ratings earn MVP and Mega MVP', async () => {
    for (let game = 0; game < 10; game++) {
      const gameId = await addCompletedGame(organizerId, [aliceId, benId]);
      await addRating(gameId, benId, aliceId, 6);
    }

    const response = await api.get('/api/users/me/badges').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', badges: ['first_game', 'games_10', 'mvp', 'mega_mvp'] });
  });
});
