import request from 'supertest';

import { createApp } from '../../../../src/app';
import { getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, cleanUpAfterTests, ONE_DAY_MS } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
});

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: the organizer edits their game that started yesterday
  // Expected status code: 409
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "game_started", error } with the reason
  test('Game already started', async () => {
    // 1. a game that started yesterday
    const gameId = await addGame(organizerId, {
      title: 'Original Title',
      startsAt: new Date(Date.now() - ONE_DAY_MS),
    });

    // 2. edit it through the interface
    const response = await request(createApp())
      .put(`/api/games/${gameId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({
        title: 'New Title',
        sport: 'basketball',
        starts_at: new Date(Date.now() + ONE_DAY_MS).toISOString(),
        location_name: 'UBC Rec Centre',
        address: '6000 Student Union Blvd',
        latitude: 49.26,
        longitude: -123.25,
        max_players: 10,
        skill_level: 'any',
      });

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(409);
    expect(response.body.msg).toBe('game_started');
    expect((await getGameById(gameId))?.title).toBe('Original Title');
  });
});
