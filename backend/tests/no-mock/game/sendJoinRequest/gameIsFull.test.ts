import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let playerId: number;
let requesterId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `send-join-request-test-gameIsFull-${firstName}`,
    email: 'send-join-request-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  requesterId = await addUser('Rae');
});

// deleting the users also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(playerId);
  await deleteUser(requesterId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: a user asks to join a game with no spots left
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "game_full", error: "This game is full" }
  test('Game is full', async () => {
    // 1. a game for two players: its organizer and one who joined
    const gameId = await createGame({
      organizerId,
      title: 'Evening Basketball',
      sport: 'basketball',
      description: null,
      startsAt: new Date(Date.now() + ONE_DAY_MS),
      endsAt: null,
      locationName: 'UBC Rec Centre',
      address: '6000 Student Union Blvd',
      latitude: 49.26,
      longitude: -123.25,
      maxPlayers: 2,
      skillLevel: 'any',
    });
    await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, playerId]);

    // 2. ask to join it through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(requesterId)}`)
      .send({ skill_level: 'beginner' });

    // 3. check it was rejected
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'game_full', error: 'This game is full' });
  });
});
