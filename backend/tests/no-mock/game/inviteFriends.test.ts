import {
  addCancelledGame,
  addFriends,
  addGame,
  addPlayer,
  addStartedGame,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  setGameStatus,
} from '../helpers';
import { noticesAfterAWhile, waitForNotices } from '../notification/notificationHelpers';

let organizerId: number;
let friendId: number;
let playerId: number;
let strangerId: number;

// every test starts with Fay as the organizer's friend
beforeEach(async () => {
  organizerId = await addUser('Olivia');
  friendId = await addUser('Fay');
  playerId = await addUser('Paul');
  strangerId = await addUser('Sam');
  await addFriends(organizerId, friendId);
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: the organizer invites Fay and Gus, two friends not in their upcoming game
  // Expected status code: 201
  // Expected behavior: Fay and Gus each get a game_invite notification for the game saved for
  //   their Notifications screen. The invite itself isn't stored, so nobody joins
  // Expected output: { msg: "success" }
  test('Organizer invites two friends', async () => {
    const otherFriendId = await addUser('Gus');
    await addFriends(organizerId, otherFriendId);
    const gameId = await addGame(organizerId);

    const response = await api.post(`/api/games/${gameId}/invites`).set(asUser(organizerId))
      .send({ friend_ids: [friendId, otherFriendId] });

    expect(response.status).toBe(201);
    expect(response.body).toEqual({ msg: 'success' });
    const invite = {
      type: 'game_invite',
      title: 'Game invite',
      body: 'Olivia invited you to Evening Basketball',
      game_id: gameId,
    };
    expect(await waitForNotices(friendId)).toEqual([invite]);
    expect(await waitForNotices(otherFriendId)).toEqual([invite]);
  });

  // Input: the organizer invites their friend to a game that is cancelled, has started,
  //   is completed, or is full
  // Expected status code: 409
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "game_cancelled" }, { msg: "game_started" } or { msg: "game_full" }
  test.each([
    ['cancelled', 'game_cancelled', () => addCancelledGame(organizerId)],
    ['started', 'game_started', () => addStartedGame(organizerId)],
    ['completed', 'game_started', async () => {
      const gameId = await addStartedGame(organizerId);
      await setGameStatus(gameId, 'completed');
      return gameId;
    }],
    ['full', 'game_full', async () => {
      const gameId = await addGame(organizerId, { maxPlayers: 2 });
      await addPlayer(gameId, playerId);
      return gameId;
    }],
  ] as const)('Organizer cannot invite to a %s game', async (_name, msg, setUpGame) => {
    const gameId = await setUpGame();

    const response = await api.post(`/api/games/${gameId}/invites`).set(asUser(organizerId))
      .send({ friend_ids: [friendId] });

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe(msg);
  });

  // Input: the organizer invites a friend and another friend who is already in the game
  // Expected status code: 409
  // Expected behavior: the whole invite is rejected
  // Expected output: { msg: "already_player", error: "One of these friends is already in this game" }
  test('Organizer cannot invite a friend already in the game', async () => {
    // 1. an upcoming game with one of the organizer's two friends in it
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);
    await addFriends(organizerId, playerId);

    // 2. invite both friends through the interface
    const response = await api.post(`/api/games/${gameId}/invites`).set(asUser(organizerId))
      .send({ friend_ids: [friendId, playerId] });

    // 3. check it was rejected
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'already_player', error: 'One of these friends is already in this game' });
  });

  // Input: friend_ids missing, empty, or with something that isn't a user id
  // Expected status code: 400
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "invalid_request", error: "friend_ids must be a list of 1 to 100 user ids" }
  test.each([
    ['missing', {}],
    ['empty', { friend_ids: [] }],
    ['text', { friend_ids: ['5'] }],
    ['not whole', { friend_ids: [5.5] }],
    ['zero', { friend_ids: [0] }],
  ])('friend_ids %s is rejected', async (_name, body) => {
    const gameId = await addGame(organizerId);

    const response = await api.post(`/api/games/${gameId}/invites`).set(asUser(organizerId))
      .send(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'friend_ids must be a list of 1 to 100 user ids',
    });
  });

  // Input: the organizer invites a friend and a user who isn't their friend
  // Expected status code: 403
  // Expected behavior: the whole invite is rejected, not even the friend is invited
  // Expected output: { msg: "not_friend", error: "You can only invite your friends" }
  test('Organizer cannot invite someone who is not their friend', async () => {
    // 1. an upcoming game, and one friend of the organizer
    const gameId = await addGame(organizerId);

    // 2. invite the friend and the stranger through the interface
    const response = await api.post(`/api/games/${gameId}/invites`).set(asUser(organizerId))
      .send({ friend_ids: [friendId, strangerId] });

    // 3. check it was rejected and nobody got an invite
    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'not_friend', error: 'You can only invite your friends' });
    expect(await noticesAfterAWhile(friendId)).toEqual([]);
    expect(await noticesAfterAWhile(strangerId)).toEqual([]);
  });

  // Input: a player who isn't the organizer invites their friend
  // Expected status code: 403
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "forbidden", error: "Only the organizer can invite friends to this game" }
  test('A player who is not the organizer cannot invite', async () => {
    // 1. an upcoming game the player joined, and the player's friend
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);
    await addFriends(playerId, strangerId);

    // 2. invite through the interface
    const response = await api.post(`/api/games/${gameId}/invites`).set(asUser(playerId))
      .send({ friend_ids: [strangerId] });

    // 3. check it was rejected
    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can invite friends to this game' });
  });
});
