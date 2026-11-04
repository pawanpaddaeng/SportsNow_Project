import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { addGame, addPlayer, addUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let friendId: number;
let playingFriendId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  friendId = await addUser('Fay');
  playingFriendId = await addUser('Paul');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: the organizer invites a friend and another friend who is already in the game
  // Expected status code: 409
  // Expected behavior: the whole invite is rejected
  // Expected output: { msg: "already_player", error: "One of these friends is already in this game" }
  test('Organizer cannot invite a friend already in the game', async () => {
    // 1. an upcoming game with one of the organizer's two friends in it
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playingFriendId);
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
