import request from 'supertest';

import { createApp } from '../../../src/app';
import * as gamesDb from '../../../src/game/db/games';
import * as ratingsDb from '../../../src/review/db/ratings';
import type * as MockEnv from '../mockEnv';
import {
  api,
  type ApiReply,
  cleanUpAfterTests,
  GAME_ID,
  signIn,
  testGame,
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
  // Mocked behavior: createRating is replaced so only the route runs
  // Input: a player rates another player in every category
  // Expected status code: 201
  // Expected behavior: createRating gets the rating with the signed in user as the rater
  // Expected output: { msg: "success" }
  test('Records a rating', async () => {
    const player = testPlayer();
    const token = signIn(player);
    const createRating = jest.spyOn(ratingsDb, 'createRating').mockResolvedValue({ ok: true });

    const response = await postRating(token, VALID_RATING);

    expect(response.status).toBe(201);
    expect(response.body).toEqual({ msg: 'success' });
    expect(createRating).toHaveBeenCalledWith(GAME_ID, player.userId, RATED_ID, {
      punctuality: 5, sportsmanship: 4, skill: 3, reliability: 2, overall: 1,
    });
  });

  // Mocked behavior: createRating is replaced to check that it's never reached
  // Input: a rating with skill left out and overall above 5
  // Expected status code: 400
  // Expected behavior: nothing is recorded
  // Expected output: { msg: "invalid_request" } with an error naming both categories
  test('Rejects missing and out of range categories', async () => {
    const token = signIn(testPlayer());
    const createRating = jest.spyOn(ratingsDb, 'createRating').mockResolvedValue({ ok: true });

    const response = await postRating(token, { ...VALID_RATING, skill: undefined, overall: 6 });

    expect(response.status).toBe(400);
    expect(response.body.msg).toBe('invalid_request');
    expect(JSON.stringify(response.body)).toContain('skill, overall');
    expect(createRating).not.toHaveBeenCalled();
  });

  // Mocked behavior: createRating reports each problem a rating can have
  // Input: a valid rating
  // Expected status code: the status of each problem
  // Expected behavior: nothing else happens
  // Expected output: the problem's msg
  test.each([
    ['not_allowed', 403, 'forbidden'],
    ['self', 409, 'rated_self'],
    ['player_not_found', 404, 'player_not_found'],
    ['already_rated', 409, 'already_rated'],
  ] as const)('Problem %s', async (problem, status, msg) => {
    const token = signIn(testPlayer());
    jest.spyOn(ratingsDb, 'createRating').mockResolvedValue({ ok: false, problem });

    const response = await postRating(token, VALID_RATING);

    expect(response.status).toBe(status);
    expect(response.body.msg).toBe(msg);
  });
});

// Interface GET /api/users/me/stats
describe('Mocked: GET /api/users/me/stats', () => {
  // Mocked behavior: getReputationScore is replaced so only the route runs
  // Input: a signed in player asks for their stats
  // Expected status code: 200
  // Expected behavior: the score is looked up for the signed in user
  // Expected output: { msg: "success", reputation_score }
  test('Returns the signed in user\'s score', async () => {
    const player = testPlayer();
    const token = signIn(player);
    const getReputationScore = jest.spyOn(ratingsDb, 'getReputationScore').mockResolvedValue(4.2);

    const response = await api.get('/api/users/me/stats').set(withToken(token));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', reputation_score: 4.2 });
    expect(getReputationScore).toHaveBeenCalledWith(player.userId);
  });

  // Mocked behavior: none, the request is refused before any lookup
  // Input: no sign in
  // Expected status code: 400 or above, requireAuth refuses it
  // Expected behavior: no score is read
  // Expected output: an error
  test('Needs a signed in user', async () => {
    const getReputationScore = jest.spyOn(ratingsDb, 'getReputationScore').mockResolvedValue(4.2);

    const response = await request(createApp()).get('/api/users/me/stats');

    expect(response.status).toBeGreaterThanOrEqual(400);
    expect(getReputationScore).not.toHaveBeenCalled();
  });
});

// Interface GET /api/games/{gameId}/ratings
describe('Mocked: GET /api/games/{gameId}/ratings', () => {
  // Mocked behavior: the game and the players to rate are replaced
  // Input: a player who showed up opens the list
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: the players and who was rated, with no scores anywhere in the reply
  test('Lists the players to rate without any scores', async () => {
    const token = signIn(testPlayer());
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame({ status: 'completed' }));
    jest.spyOn(ratingsDb, 'getPlayersToRate').mockResolvedValue([
      { userId: RATED_ID, firstName: 'Rae', lastName: 'Rated', rated: true },
    ]);

    const response = await api.get(`/api/games/${GAME_ID}/ratings`).set(withToken(token));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      msg: 'success',
      game_title: 'Sunday Soccer',
      organizer_id: 1,
      players: [{ user_id: RATED_ID, first_name: 'Rae', last_name: 'Rated', rated: true }],
    });
  });

  // Mocked behavior: getPlayersToRate says this user can't rate in the game
  // Input: a no-show, or anyone before the game is completed, opens the list
  // Expected status code: 403
  // Expected behavior: nothing changes
  // Expected output: { msg: "forbidden" }
  test('A no-show cannot open the list', async () => {
    const token = signIn(testPlayer());
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame({ status: 'completed' }));
    jest.spyOn(ratingsDb, 'getPlayersToRate').mockResolvedValue(null);

    const response = await api.get(`/api/games/${GAME_ID}/ratings`).set(withToken(token));

    expect(response.status).toBe(403);
    expect(response.body.msg).toBe('forbidden');
  });
});
