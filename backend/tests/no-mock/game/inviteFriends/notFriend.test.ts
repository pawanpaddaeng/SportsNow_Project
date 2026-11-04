import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let friendId: number;
let strangerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  friendId = await addUser('Fay');
  strangerId = await addUser('Sam');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: the organizer invites a friend and a user who isn't their friend
  // Expected status code: 403
  // Expected behavior: the whole invite is rejected, not even the friend is invited
  // Expected output: { msg: "not_friend", error: "You can only invite your friends" }
  test('Organizer cannot invite someone who is not their friend', async () => {
    // 1. an upcoming game, and one friend of the organizer
    const gameId = await addGame(organizerId);
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
