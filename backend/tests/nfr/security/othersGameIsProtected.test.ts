import { addJoinRequest, addPlayer, addUser, addGame, asUser, cleanUpAfterTests, validGameBody } from '../../no-mock/helpers';
import { gameChatId, addMessage } from '../../no-mock/chat/chatHelpers';
import { callRoute, fillPath, type Route, routeName, savedState } from './securityHelpers';

/*  NFR 1 (Security): a signed in user can only read and send messages in chats they belong to,
    and can only change games and join requests they own. Here another signed in user, who
    isn't in the organiser's game, tries to read its chat and to take over the game.
 */

// what the outsider tries on the organiser's game, each must be turned down with 403
const ATTEMPTS: Route[] = [
  { method: 'get', path: '/api/chats/:chatId/messages' },
  { method: 'put', path: '/api/games/:gameId', body: validGameBody({ title: 'Changed by an outsider' }) },
  { method: 'post', path: '/api/games/:gameId/cancel' },
  { method: 'post', path: '/api/games/:gameId/complete' },
  { method: 'delete', path: '/api/games/:gameId/players/:userId' },
  { method: 'get', path: '/api/games/:gameId/join-requests' },
  { method: 'patch', path: '/api/games/:gameId/join-requests/:requestId', body: { decision: 'approve' } },
  { method: 'post', path: '/api/games/:gameId/invites', body: { friend_ids: [1] } },
];

cleanUpAfterTests();

// Interfaces: GET /api/chats/{chatId}/messages, PUT /api/games/{gameId},
//   POST /api/games/{gameId}/cancel, POST /api/games/{gameId}/complete,
//   DELETE /api/games/{gameId}/players/{userId}, GET /api/games/{gameId}/join-requests,
//   PATCH /api/games/{gameId}/join-requests/{requestId}, POST /api/games/{gameId}/invites
describe("NFR 1: a signed in user can't use someone else's game or chat", () => {
  // Input: an outsider, signed in with their own valid token but not in the organiser's game,
  //   reads its chat, edits, cancels and completes it, removes its player, lists and approves
  //   its join request, and invites to it
  // Expected status code: 403 for each
  // Expected behavior: nothing saved about the organiser, their game or its chat changes
  // Expected output: { msg: "forbidden" } for each
  test("An outsider can't read the chat of, or change, someone else's game", async () => {
    // 1. an organiser's game with a player, a chat message and a pending join request
    const organizerId = await addUser('Organiser');
    const playerId = await addUser('Player');
    const requesterId = await addUser('Requester');
    const outsiderId = await addUser('Outsider');
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);
    const chatId = await gameChatId(gameId);
    await addMessage(chatId, organizerId, 'Meet at court 2');
    const joinRequestId = await addJoinRequest(gameId, requesterId);
    const ids = { gameId, userId: playerId, chatId, joinRequestId, friendRequestId: 0 };

    const before = await savedState(organizerId, gameId, chatId);

    // 2. the outsider tries each one with their own valid token, and is turned down
    for (const attempt of ATTEMPTS) {
      const response = await callRoute(attempt, fillPath(attempt.path, ids), asUser(outsiderId));
      const { msg } = response.body as { msg?: string };
      expect({ route: routeName(attempt), status: response.status, msg })
        .toEqual({ route: routeName(attempt), status: 403, msg: 'forbidden' });
    }

    // 3. check nothing saved about the organiser, their game or its chat changed
    expect(await savedState(organizerId, gameId, chatId)).toEqual(before);
  });
});
