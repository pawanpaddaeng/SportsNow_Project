import request from 'supertest';

import { createApp } from '../../../../src/app';
import { getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addPlayer, addUser, cleanUpAfterTests, ONE_DAY_MS } from '../../helpers';

let organizerId: number;
let playerIds: number[];

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerIds = [await addUser('Paul'), await addUser('Pia')];
});

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: the organizer lowers max_players to 2 when the game has 3 players
  // Expected status code: 400
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "invalid_request", error: "max_players can't be below the 3 players already in the game" }
  test('Too few spots', async () => {
    // 1. a game with 3 players: the organizer and two who joined
    const gameId = await addGame(organizerId, { title: 'Original Title' });
    await Promise.all(playerIds.map((playerId) => addPlayer(gameId, playerId)));

    // 2. lower max_players below that through the interface
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
        max_players: 2,
        skill_level: 'any',
      });

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: "max_players can't be below the 3 players already in the game",
    });
    const saved = await getGameById(gameId);
    expect(saved?.title).toBe('Original Title');
    expect(saved?.maxPlayers).toBe(10);
  });
});
