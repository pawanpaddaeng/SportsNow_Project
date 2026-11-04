import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let friendId: number;
let playingFriendId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `invite-friends-test-friendAlreadyInGame-${firstName}`,
    email: 'invite-friends-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  friendId = await addUser('Fay');
  playingFriendId = await addUser('Paul');
});

// deleting the users also deletes their games, game players, chats and friendships
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(friendId);
  await deleteUser(playingFriendId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: the organizer invites a friend and another friend who is already in the game
  // Expected status code: 409
  // Expected behavior: the whole invite is rejected
  // Expected output: { msg: "already_player", error: "One of these friends is already in this game" }
  test('Organizer cannot invite a friend already in the game', async () => {
    // 1. an upcoming game with one of the organizer's two friends in it
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
    await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, playingFriendId]);
    await executeWrite('INSERT INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?), (?, ?), (?, ?)', [
      organizerId, friendId, friendId, organizerId,
      organizerId, playingFriendId, playingFriendId, organizerId,
    ]);

    // 2. invite both friends through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/invites`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ friend_ids: [friendId, playingFriendId] });

    // 3. check it was rejected
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'already_player', error: 'One of these friends is already in this game' });
  });
});
