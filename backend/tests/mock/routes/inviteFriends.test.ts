import request from 'supertest';

import { createApp } from '../../../src/app';
import * as friendsDb from '../../../src/friend/db/friends';
import * as gamesDb from '../../../src/game/db/games';
import { type Game } from '../../../src/game/db/games';
import * as push from '../../../src/core/notifications/push';
import type * as MockEnv from '../mockEnv';
import { cleanUpAfterTests, signIn, testGame, testOrganizer, testPlayer } from '../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

const GAME_ID = 42;
// the organizer's friends, neither of them in the game yet
const FRIEND_IDS = [5, 6];
// someone in the game who isn't one of the organizer's friends
const PLAYER_ID = 2;

cleanUpAfterTests();

/*  replaces the database with game, its players (the organizer and player 2) and the
    organizer's friends (FRIEND_IDS), and push notifications with a spy. Returns the spy
 */
function mockDatabase(game: Game, friendIds: number[] = FRIEND_IDS) {
  jest.spyOn(gamesDb, 'getGameById').mockImplementation((gameId) =>
    Promise.resolve(gameId === game.gameId ? game : null));
  jest.spyOn(gamesDb, 'getGamePlayers').mockResolvedValue([
    { userId: game.organizerId, firstName: 'Olivia', lastName: 'Organizer' },
    { userId: PLAYER_ID, firstName: 'Paul', lastName: 'Player' },
  ]);
  jest.spyOn(friendsDb, 'areFriends').mockImplementation((userId, otherUserId) =>
    Promise.resolve(userId === game.organizerId && friendIds.includes(otherUserId)));
  return jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(0);
}

// Interface POST /api/games/{gameId}/invites
describe('Mocked: POST /api/games/{gameId}/invites', () => {
  // Mocked behavior: the database is replaced by upcoming game 42 with 3 of 10 players,
  //   and the organizer's friends 5 and 6. Push notifications are replaced by a spy
  // Input: the organizer invites friends 5 and 6
  // Expected status code: 201
  // Expected behavior: friends 5 and 6 each get a "game_invite" notification for game 42
  // Expected output: { msg: "success" }
  test('Organizer invites two friends', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    const sendPush = mockDatabase(testGame({ gameId: GAME_ID, organizerId: organizer.userId }));

    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/invites`)
      .set('Authorization', `Bearer ${token}`)
      .send({ friend_ids: FRIEND_IDS });

    expect(response.status).toBe(201);
    expect(response.body).toEqual({ msg: 'success' });
    expect(sendPush).toHaveBeenCalledTimes(1);
    expect(sendPush).toHaveBeenCalledWith(FRIEND_IDS, {
      title: 'Game invite',
      body: 'Olivia invited you to Sunday Soccer',
      data: { type: 'game_invite', gameId: String(GAME_ID) },
    });
  });

  // Mocked behavior: as above
  // Input: player 2, who isn't the organizer, invites friend 5
  // Expected status code: 403
  // Expected behavior: nobody is notified
  // Expected output: { msg: "forbidden" }
  test('A player who is not the organizer cannot invite', async () => {
    const token = signIn(testPlayer());
    const sendPush = mockDatabase(testGame({ gameId: GAME_ID, organizerId: testOrganizer().userId }));

    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/invites`)
      .set('Authorization', `Bearer ${token}`)
      .send({ friend_ids: [5] });

    expect(response.status).toBe(403);
    expect(response.body.msg).toBe('forbidden');
    expect(sendPush).not.toHaveBeenCalled();
  });

  // Mocked behavior: as above, user 7 is not the organizer's friend
  // Input: the organizer invites friend 5 and user 7
  // Expected status code: 403
  // Expected behavior: nobody is notified, not even friend 5
  // Expected output: { msg: "not_friend" }
  test('Organizer cannot invite someone who is not their friend', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    const sendPush = mockDatabase(testGame({ gameId: GAME_ID, organizerId: organizer.userId }));

    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/invites`)
      .set('Authorization', `Bearer ${token}`)
      .send({ friend_ids: [5, 7] });

    expect(response.status).toBe(403);
    expect(response.body.msg).toBe('not_friend');
    expect(sendPush).not.toHaveBeenCalled();
  });

  // Mocked behavior: as above, player 2 is also the organizer's friend
  // Input: the organizer invites friend 5 and player 2
  // Expected status code: 409
  // Expected behavior: nobody is notified
  // Expected output: { msg: "already_player" }
  test('Organizer cannot invite a friend already in the game', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    const game = testGame({ gameId: GAME_ID, organizerId: organizer.userId });
    const sendPush = mockDatabase(game, [...FRIEND_IDS, PLAYER_ID]);

    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/invites`)
      .set('Authorization', `Bearer ${token}`)
      .send({ friend_ids: [5, PLAYER_ID] });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe('already_player');
    expect(sendPush).not.toHaveBeenCalled();
  });

  // Mocked behavior: as above, but game 42 is cancelled, has started, or is full
  // Input: the organizer invites friend 5
  // Expected status code: 409
  // Expected behavior: nobody is notified
  // Expected output: { msg: "game_cancelled" }, { msg: "game_started" } or { msg: "game_full" }
  test.each([
    ['cancelled', { status: 'cancelled' }, 'game_cancelled'],
    ['started', { hasStarted: true }, 'game_started'],
    ['completed', { status: 'completed', hasStarted: true }, 'game_started'],
    ['full', { playerCount: 10, maxPlayers: 10 }, 'game_full'],
  ] as const)('Organizer cannot invite to a %s game', async (_name, changes, msg) => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    const sendPush = mockDatabase(testGame({ gameId: GAME_ID, organizerId: organizer.userId, ...changes }));

    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/invites`)
      .set('Authorization', `Bearer ${token}`)
      .send({ friend_ids: [5] });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
    expect(sendPush).not.toHaveBeenCalled();
  });

  // Mocked behavior: as above
  // Input: friend_ids missing, empty, or with something that isn't a user id
  // Expected status code: 400
  // Expected behavior: nobody is notified
  // Expected output: { msg: "invalid_request" }
  test.each([
    ['missing', {}],
    ['empty', { friend_ids: [] }],
    ['text', { friend_ids: ['5'] }],
    ['not whole', { friend_ids: [5.5] }],
    ['zero', { friend_ids: [0] }],
  ])('friend_ids %s is rejected', async (_name, body) => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    const sendPush = mockDatabase(testGame({ gameId: GAME_ID, organizerId: organizer.userId }));

    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/invites`)
      .set('Authorization', `Bearer ${token}`)
      .send(body);

    expect(response.status).toBe(400);
    expect(response.body.msg).toBe('invalid_request');
    expect(sendPush).not.toHaveBeenCalled();
  });

  // Mocked behavior: getGameById throws as if the database were down
  // Input: the organizer invites friend 5
  // Expected status code: 500
  // Expected behavior: nobody is notified
  // Expected output: { msg: "server_error" }
  test('Database error', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    const sendPush = mockDatabase(testGame({ gameId: GAME_ID, organizerId: organizer.userId }));
    jest.spyOn(gamesDb, 'getGameById').mockRejectedValue(new Error('database is down'));

    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/invites`)
      .set('Authorization', `Bearer ${token}`)
      .send({ friend_ids: [5] });

    expect(response.status).toBe(500);
    expect(response.body.msg).toBe('server_error');
    expect(sendPush).not.toHaveBeenCalled();
  });
});