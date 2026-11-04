import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let requesterId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `send-join-request-test-cancelledOrStarted-${firstName}`,
    email: 'send-join-request-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

// a real game of the organizer's starting at the given time
function addGame(startsAt: Date): Promise<number> {
  return createGame({
    organizerId,
    title: 'Evening Basketball',
    sport: 'basketball',
    description: null,
    startsAt,
    endsAt: null,
    locationName: 'UBC Rec Centre',
    address: '6000 Student Union Blvd',
    latitude: 49.26,
    longitude: -123.25,
    maxPlayers: 10,
    skillLevel: 'any',
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

// deleting the users also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(requesterId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: a user asks to join a cancelled game, and a game that already started
  // Expected status code: 409
  // Expected behavior: the request is rejected
  // Expected output: { msg: "game_cancelled" } and { msg: "game_started" }
  test.each([
    ['cancelled', 'game_cancelled', async () => {
      const gameId = await addGame(new Date(Date.now() + ONE_DAY_MS));
      await executeWrite("UPDATE games SET status = 'cancelled' WHERE game_id = ?", [gameId]);
      return gameId;
    }],
    ['started', 'game_started', () => addGame(new Date(Date.now() - ONE_DAY_MS))],
  ] as const)('Asking to join a %s game', async (_name, msg, setUpGame) => {
    const gameId = await setUpGame();

    const response = await request(createApp())
      .post(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(requesterId)}`)
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
  });
});
