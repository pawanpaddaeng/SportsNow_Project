import { addJoinRequest, addPlayer, addUser, addGame, cleanUpAfterTests } from '../../no-mock/helpers';
import { addFriendRequest } from '../../no-mock/friend/friendHelpers';
import { gameChatId, addMessage } from '../../no-mock/chat/chatHelpers';
import { addNotification } from '../../no-mock/notification/notificationHelpers';
import { deleteUser } from '../../../src/user/db/users';
import { saveDeviceToken } from '../../../src/core/notifications/deviceTokens';
import { badSignIns, callRoute, fillPath, PROTECTED_ROUTES, routeName, savedState } from './securityHelpers';

/*  NFR 1 (Security): only signed in users can use SportsNow. Every request except sign up
    and login must carry a valid, unexpired token from an account that still exists, or it's
    turned down and nothing is read or changed. Each bad token here claims to be the victim,
    as an attacker trying to get into their account would.
 */

// 29 routes x 6 bad sign ins is 174 requests, more than Jest's default 5 seconds
jest.setTimeout(60_000);

cleanUpAfterTests();

// Interface: every route in PROTECTED_ROUTES (everyRouteIsChecked.test.ts checks it lists them all)
describe('NFR 1: every route turns down a bad sign in', () => {
  // Input: each protected route, called on the victim's game, chat, requests and account with:
  //   no token, a token that isn't a JWT, a token signed with the wrong secret, a token with no
  //   signature, an expired token, and a token for a deleted account
  // Expected status code: 400 with no token, 401 for the others
  // Expected behavior: nothing saved about the victim changes
  // Expected output: { msg: "invalid_request" }, then { msg: "invalid_token" } three times,
  //   { msg: "expired_token" } and { msg: "no_user" }
  test('Every protected route turns down every bad sign in and changes nothing', async () => {
    // 1. a victim with a game, a player, a join request, a friend request, a chat message,
    //    a notification and a device, so every route has something it could change
    const victimId = await addUser('Victim');
    const playerId = await addUser('Player');
    const requesterId = await addUser('Requester');
    const gameId = await addGame(victimId);
    await addPlayer(gameId, playerId);
    const chatId = await gameChatId(gameId);
    await addMessage(chatId, victimId, 'Bring the ball');
    const joinRequestId = await addJoinRequest(gameId, requesterId);
    const friendRequestId = await addFriendRequest(requesterId, victimId);
    await addNotification(victimId);
    await saveDeviceToken('victim-device', victimId);
    const ids = { gameId, userId: playerId, chatId, joinRequestId, friendRequestId };

    // 2. a user whose token was made before their account was deleted
    const deletedUserId = await addUser('Deleted');
    const signIns = badSignIns(victimId, deletedUserId);
    await deleteUser(deletedUserId);

    const before = await savedState(victimId, gameId, chatId);

    // 3. call every route with every bad sign in, and check each is turned down
    for (const route of PROTECTED_ROUTES) {
      for (const signIn of signIns) {
        const response = await callRoute(route, fillPath(route.path, ids), signIn.headers);
        const { msg } = response.body as { msg?: string };
        const tried = { route: routeName(route), signIn: signIn.name };
        expect({ ...tried, status: response.status, msg }).toEqual({ ...tried, status: signIn.status, msg: signIn.msg });
      }
    }

    // 4. check nothing saved about the victim changed
    expect(await savedState(victimId, gameId, chatId)).toEqual(before);
  });
});
