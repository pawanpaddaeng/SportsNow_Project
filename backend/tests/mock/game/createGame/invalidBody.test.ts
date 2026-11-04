import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testOrganizer, validGameBody, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface POST /api/games
describe('Mocked: POST /api/games', () => {
  // Mocked behavior: createGame is only watched, to check it doesn't run.
  //   Sign in is mocked only so the route can run
  // Input: a valid game without its title, from a signed in user
  // Expected status code: 400
  // Expected behavior: the request is rejected before anything reaches the database
  // Expected output: { msg: "invalid_request", error: "title is required (max 100 characters)" }
  test('Invalid body never reaches the database', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. watch the save
    const createGame = jest.spyOn(gamesDb, 'createGame');

    // 3. send a game with no title through the interface
    const response = await withToken(token).post('/api/games')
      .send(validGameBody({ title: undefined }));

    // 4. check it was rejected and nothing was saved
    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'title is required (max 100 characters)' });
    expect(createGame).not.toHaveBeenCalled();
  });
});
