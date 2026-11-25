import * as ratingsDb from '../../../src/review/db/ratings';
import type * as MockEnv from '../mockEnv';
import {
  api,
  type ApiReply,
  cleanUpAfterTests,
  GAME_ID,
  signIn,
  testPlayer,
  withToken,
} from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

const RATED_ID = 7;
const VALID_RATING = { rated_user_id: RATED_ID, punctuality: 5, sportsmanship: 4, skill: 3, reliability: 2, overall: 1 };

cleanUpAfterTests();

async function postRating(token: string, rating: object): Promise<ApiReply> {
  const response = await api.post(`/api/games/${GAME_ID}/ratings`).set(withToken(token))
    .send(rating);
  return response as ApiReply;
}

// Interface POST /api/games/{gameId}/ratings
describe('Mocked: POST /api/games/{gameId}/ratings', () => {
  // Mocked behavior: createRating reports each problem a rating can have
  // Input: a valid rating
  // Expected status code: the status of each problem
  // Expected behavior: nothing else happens
  // Expected output: the problem's msg
  test.each([
    ['not_allowed', 403, 'forbidden'],
    ['rating_closed', 403, 'rating_closed'],
    ['self', 409, 'rated_self'],
    ['player_not_found', 404, 'player_not_found'],
    ['already_rated', 409, 'already_rated'],
  ] as const)('Each problem a rating can have: %s', async (problem, status, msg) => {
    const token = signIn(testPlayer());
    jest.spyOn(ratingsDb, 'createRating').mockResolvedValue({ ok: false, problem });

    const response = await postRating(token, VALID_RATING);

    expect(response.status).toBe(status);
    expect(response.body.msg).toBe(msg);
  });

  // Mocked behavior: createRating throws, as when the database is down
  // Input: a valid rating
  // Expected status code: 500
  // Expected behavior: the error is handled and the server keeps running
  // Expected output: { msg: "server_error", error: "Could not submit your rating, try again later" }
  test('Database fails', async () => {
    const token = signIn(testPlayer());
    const createRating = jest.spyOn(ratingsDb, 'createRating').mockRejectedValue(new Error('Forced error'));

    const response = await postRating(token, VALID_RATING);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not submit your rating, try again later' });
    expect(createRating).toHaveBeenCalledTimes(1);
  });
});