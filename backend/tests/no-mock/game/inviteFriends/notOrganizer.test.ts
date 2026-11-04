import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let playerId: number;
let friendId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `invite-friends-test-notOrganizer-${firstName}`,
    email: 'invite-friends-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Paul');
  friendId = await addUser('Fay');
});

// deleting the users also deletes their games, game players, chats and friendships
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(playerId);
  await deleteUser(friendId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: a player who isn't the organizer invites their friend
  // Expected status code: 403
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "forbidden", error: "Only the organizer can invite friends to this game" }
  test('A player who is not the organizer cannot invite', async () => {
    // 1. an upcoming game the player joined, and the player's friend
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
      maxPlayers: 10,
      skillLevel: 'any',
    });
    await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, playerId]);
    await executeWrite('INSERT INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)',
      [playerId, friendId, friendId, playerId]);

    // 2. invite through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/invites`)
      .set('Authorization', `Bearer ${createAuthToken(playerId)}`)
      .send({ friend_ids: [friendId] });

    // 3. check it was rejected
    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can invite friends to this game' });
  });
});
