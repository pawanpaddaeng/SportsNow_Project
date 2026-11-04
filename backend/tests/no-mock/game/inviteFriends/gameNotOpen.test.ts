import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let friendId: number;
let playerId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `invite-friends-test-${Date.now()}-${Math.random()}`,
    email: 'invite-friends-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

// a real game of the organizer's, with room for the given number of players
function addGame(startsAt: Date, maxPlayers: number): Promise<number> {
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
    maxPlayers,
    skillLevel: 'any',
  });
}

function tomorrow(): Date {
  return new Date(Date.now() + ONE_DAY_MS);
}

function yesterday(): Date {
  return new Date(Date.now() - ONE_DAY_MS);
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  friendId = await addUser('Fay');
  playerId = await addUser('Paul');
  await executeWrite('INSERT INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)',
    [organizerId, friendId, friendId, organizerId]);
});

// deleting the users also deletes their games, game players, chats and friendships
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(friendId);
  await deleteUser(playerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: the organizer invites their friend to a game that is cancelled, has started,
  //   is completed, or is full
  // Expected status code: 409
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "game_cancelled" }, { msg: "game_started" } or { msg: "game_full" }
  test.each([
    ['cancelled', 'game_cancelled', async () => {
      const gameId = await addGame(tomorrow(), 10);
      await executeWrite("UPDATE games SET status = 'cancelled' WHERE game_id = ?", [gameId]);
      return gameId;
    }],
    ['started', 'game_started', () => addGame(yesterday(), 10)],
    ['completed', 'game_started', async () => {
      const gameId = await addGame(yesterday(), 10);
      await executeWrite("UPDATE games SET status = 'completed' WHERE game_id = ?", [gameId]);
      return gameId;
    }],
    ['full', 'game_full', async () => {
      const gameId = await addGame(tomorrow(), 2);
      await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, playerId]);
      return gameId;
    }],
  ] as const)('Organizer cannot invite to a %s game', async (_name, msg, setUpGame) => {
    const gameId = await setUpGame();

    const response = await request(createApp())
      .post(`/api/games/${gameId}/invites`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ friend_ids: [friendId] });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
  });
});
