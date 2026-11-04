import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let friendId: number;
let strangerId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `invite-friends-test-${Date.now()}-${Math.random()}`,
    email: 'invite-friends-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  friendId = await addUser('Fay');
  strangerId = await addUser('Sam');
});

// deleting the users also deletes their games, game players, chats and friendships
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(friendId);
  await deleteUser(strangerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: the organizer invites a friend and a user who isn't their friend
  // Expected status code: 403
  // Expected behavior: the whole invite is rejected, not even the friend is invited
  // Expected output: { msg: "not_friend", error: "You can only invite your friends" }
  test('Organizer cannot invite someone who is not their friend', async () => {
    // 1. an upcoming game, and one friend of the organizer
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
    await executeWrite('INSERT INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)',
      [organizerId, friendId, friendId, organizerId]);

    // 2. invite the friend and the stranger through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/invites`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ friend_ids: [friendId, strangerId] });

    // 3. check it was rejected
    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'not_friend', error: 'You can only invite your friends' });
  });
});
