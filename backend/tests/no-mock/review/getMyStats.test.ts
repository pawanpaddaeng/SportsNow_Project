import { addStartedGame, addUser, api, asUser, cleanUpAfterTests, withToken } from '../helpers';
import { addAttendance, addCompletedGame, addRating } from './reviewHelpers';

// every expected score is (3 × 5 + Σ weight × game score) / (3 + Σ weight), rounded to two
// decimal places, as section 4.1 of the design doc gives it. The newest game has weight 1 and
// each older one 0.9 times the weight of the one after it

let organizerId: number;
let aliceId: number;
let benId: number;
let caraId: number;
let nedId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  aliceId = await addUser('Alice');
  benId = await addUser('Ben');
  caraId = await addUser('Cara');
  nedId = await addUser('Ned');
});

cleanUpAfterTests();

// Interface GET /api/users/me/stats
describe('Unmocked: GET /api/users/me/stats', () => {
  // Input: no Authorization header, then a token that isn't a real JWT
  // Expected status code: 400, then 401
  // Expected behavior: no score is read
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    const noToken = await api.get('/api/users/me/stats');
    expect(noToken.status).toBe(400);
    expect(noToken.body).toMatchObject({ msg: 'invalid_request' });

    const badToken = await api.get('/api/users/me/stats').set(withToken('not-a-real-token'));
    expect(badToken.status).toBe(401);
    expect(badToken.body).toEqual({ msg: 'invalid_token', error: 'Invalid jwt_token' });
  });

  // Input: a new user who never played asks for their score
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: { msg: "success", reputation_score: null }, shown as "Not rated yet"
  test('A new player is not rated yet', async () => {
    const response = await api.get('/api/users/me/stats').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', reputation_score: null });
  });

  // Input: Alice showed up to a completed game and rated Ben, but nobody rated her
  // Expected status code: 200
  // Expected behavior: playing, or rating others, doesn't give her a score
  // Expected output: { msg: "success", reputation_score: null }
  test('A player nobody rated is not rated yet, even after playing', async () => {
    const gameId = await addCompletedGame(organizerId, [aliceId, benId]);
    await addRating(gameId, aliceId, benId, 6);

    const response = await api.get('/api/users/me/stats').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', reputation_score: null });
  });

  // Input: Alice's only game, where its only other player, the organizer, gave her 6 stars
  // Expected status code: 200
  // Expected behavior: one game only moves the score part of the way from 5: (15 + 6) / 4
  // Expected output: { msg: "success", reputation_score: 5.25 }
  test('One game rated 6 by its only other player gives 5.25', async () => {
    const gameId = await addCompletedGame(organizerId, [aliceId]);
    await addRating(gameId, organizerId, aliceId, 6);

    const response = await api.get('/api/users/me/stats').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', reputation_score: 5.25 });
  });

  // Input: a game with Alice, the organizer, Ben, Cara and Ned, who didn't show up. Only the
  //   organizer rated Alice, 6 stars
  // Expected status code: 200
  // Expected behavior: Ben and Cara showed up but didn't rate, so each counts as 5. Ned didn't
  //   show up, so he doesn't count. The game scores (6 + 5 + 5) / 3: (15 + 5.33) / 4
  // Expected output: { msg: "success", reputation_score: 5.08 }
  test('Players who showed up but did not rate count as 5, no-shows do not count', async () => {
    const gameId = await addCompletedGame(organizerId, [aliceId, benId, caraId, nedId], [nedId]);
    await addRating(gameId, organizerId, aliceId, 6);

    const response = await api.get('/api/users/me/stats').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', reputation_score: 5.08 });
  });

  // Input: Ned didn't show up to his only game
  // Expected status code: 200
  // Expected behavior: the no-show counts as a game scoring 1: (15 + 1) / 4
  // Expected output: { msg: "success", reputation_score: 4 }
  test('A no-show counts as a game scoring 1', async () => {
    await addCompletedGame(organizerId, [aliceId, nedId], [nedId]);

    const response = await api.get('/api/users/me/stats').set(asUser(nedId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', reputation_score: 4 });
  });

  // Input: two games with Alice and Ben. The game created first was played later (March 2) and
  //   Ben gave Alice 6 stars. The game created second was played earlier (March 1), 2 stars
  // Expected status code: 200
  // Expected behavior: games are ordered by when they were played, not when they were saved,
  //   so the 6 star game counts most: (15 + 6 + 0.9 × 2) / 4.9. In the wrong order it would be 4.57
  // Expected output: { msg: "success", reputation_score: 4.65 }
  test('The game played most recently counts most', async () => {
    const newerGameId = await addStartedGame(organizerId);
    const olderGameId = await addStartedGame(organizerId);
    for (const userId of [aliceId, benId]) {
      await addAttendance(newerGameId, userId, false, '2026-03-02 18:00:00');
      await addAttendance(olderGameId, userId, false, '2026-03-01 18:00:00');
    }
    await addRating(newerGameId, benId, aliceId, 6);
    await addRating(olderGameId, benId, aliceId, 2);

    const response = await api.get('/api/users/me/stats').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', reputation_score: 4.65 });
  });

  // Input: four games with Alice and Ben, played March 1 to 4. Ben gave Alice 6 stars in the
  //   first three and 1 star in the newest
  // Expected status code: 200
  // Expected behavior: the middle of her four games is 6, so the 1 star game is more than 1.5
  //   stars from it and counts a quarter as much: (15 + 0.25 × 1 + 6 × (0.9 + 0.81 + 0.729)) /
  //   (3 + 0.25 + 2.439). Counted in full it would be 4.76
  // Expected output: { msg: "success", reputation_score: 5.25 }
  test('An unusual game counts a quarter as much', async () => {
    for (const day of ['01', '02', '03', '04']) {
      const gameId = await addStartedGame(organizerId);
      for (const userId of [aliceId, benId]) {
        await addAttendance(gameId, userId, false, `2026-03-${day} 18:00:00`);
      }
      await addRating(gameId, benId, aliceId, day === '04' ? 1 : 6);
    }

    const response = await api.get('/api/users/me/stats').set(asUser(aliceId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', reputation_score: 5.25 });
  });
});