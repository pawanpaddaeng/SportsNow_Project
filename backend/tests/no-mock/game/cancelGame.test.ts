import { getGameById } from '../../../src/game/db/games';
import { createAuthToken } from '../../../src/user/auth/jwt';
import {
  addGame,
  addStartedGame,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  MISSING_GAME_ID,
  withToken,
} from '../helpers';
import { type ApiReply, expectGameReply } from '../../sharedHelpers';

let organizerId: number;
let otherUserId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  otherUserId = await addUser('Omar');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/cancel
describe('Unmocked: POST /api/games/{gameId}/cancel', () => {
  // Input: the organizer cancels their upcoming game
  // Expected status code: 200
  // Expected behavior: the game goes from "scheduled" to "cancelled" in the database
  // Expected output: { msg: "success", game } with game.status "cancelled"
  test('Organizer cancels their game', async () => {
    // 1. the organizer's upcoming game
    const gameId = await addGame(organizerId);
    expect((await getGameById(gameId))?.status).toBe('scheduled');

    // 2. cancel it through the interface
    const response = await api.post(`/api/games/${gameId}/cancel`).set(asUser(organizerId));

    // 3. check it was cancelled
    expectGameReply(response as ApiReply, { game_id: gameId, status: 'cancelled' });
    expect((await getGameById(gameId))?.status).toBe('cancelled');
  });

  // Input: the organizer cancels the same game twice
  // Expected status code: 200 the first time, then 409
  // Expected behavior: the second cancel is rejected
  // Expected output: { msg: "game_cancelled", error: "This game was cancelled" } the second time
  test('Already cancelled', async () => {
    // 1. the organizer's upcoming game
    const gameId = await addGame(organizerId);
    const token = createAuthToken(organizerId);

    // 2. cancel it once through the interface
    const first = await api.post(`/api/games/${gameId}/cancel`).set(withToken(token));
    expect(first.status).toBe(200);

    // 3. cancel it again and check that's rejected
    const second = await api.post(`/api/games/${gameId}/cancel`).set(withToken(token));
    expect(second.status).toBe(409);
    expect(second.body).toEqual({ msg: 'game_cancelled', error: 'This game was cancelled' });
  });

  // Input: the organizer cancels their game that started yesterday
  // Expected status code: 409
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "game_started", error } with the reason
  test('Game already started', async () => {
    const gameId = await addStartedGame(organizerId);

    const response = await api.post(`/api/games/${gameId}/cancel`).set(asUser(organizerId));

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe('game_started');
    expect((await getGameById(gameId))?.status).toBe('scheduled');
  });

  // Input: a signed in user cancels an id with no game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "not_found", error: "This game does not exist" }
  test("Game doesn't exist", async () => {
    const response = await api.post(`/api/games/${MISSING_GAME_ID}/cancel`).set(asUser(otherUserId));

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'not_found', error: 'This game does not exist' });
  });

  // Input: a user who isn't the organizer cancels the game
  // Expected status code: 403
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "forbidden", error: "Only the organizer can change this game" }
  test('Not the organizer', async () => {
    const gameId = await addGame(organizerId);

    const response = await api.post(`/api/games/${gameId}/cancel`).set(asUser(otherUserId));

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can change this game' });
    expect((await getGameById(gameId))?.status).toBe('scheduled');
  });
});
