import { type ChatSocket, startChatSocket } from '../../../src/chat/socket/chatSocket';
import { createAuthToken } from '../../../src/user/auth/jwt';
import { closeAllClients, openChatConnection, waitForChatServer } from '../../chatSocketClient';
import { gameChatId } from '../../no-mock/chat/chatHelpers';
import { addGame, addPlayer, addUser, cleanUpAfterTests } from '../../no-mock/helpers';
import { expiredToken, forgedToken, unsignedToken } from './securityHelpers';

/*  NFR 1 (Security): only members of a chat can open it. The chat WebSocket must turn down a
    connection with no token or a bad token, and one from a signed in user who isn't in the chat,
    before any message is sent or received.
 */

// this file's own port, test files run at the same time
const PORT = 18105;

let chatSocket: ChatSocket;

beforeAll(async () => {
  chatSocket = startChatSocket(PORT);
  expect(await waitForChatServer(PORT)).toBe(true);
});

afterAll(() => {
  chatSocket.close();
});

afterEach(() => {
  closeAllClients();
});

cleanUpAfterTests();

// Interface WebSocket connect (wss://DOMAIN/ws?chat_id=)
describe("NFR 1: the chat WebSocket can't be opened without being in the chat", () => {
  // Input: connections to a game's chat with no token, a token that isn't a JWT, a token signed
  //   with the wrong secret, a token with no signature, an expired token, and a valid token of
  //   a user who isn't in the game
  // Expected status code: 400 with no token, 401 for the bad tokens, 403 for the outsider
  // Expected behavior: no connection is opened, a player of the game still can open it
  // Expected output: the HTTP status the connection is turned down with
  test("Only a member of the chat can open it", async () => {
    // 1. a game with a player, and an outsider who isn't in it
    const organizerId = await addUser('Organiser');
    const playerId = await addUser('Player');
    const outsiderId = await addUser('Outsider');
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);
    const chatId = await gameChatId(gameId);

    // 2. each bad connection is turned down with its status, the bad tokens claim to be the player
    const attempts: [string, string | null, number][] = [
      ['no token', null, 400],
      ['not a JWT', 'not.a.jwt', 401],
      ['forged signature', forgedToken(playerId), 401],
      ['no signature', unsignedToken(playerId), 401],
      ['expired', expiredToken(playerId), 401],
      ['outsider', createAuthToken(outsiderId), 403],
    ];
    for (const [name, token, status] of attempts) {
      const result = await openChatConnection(PORT, chatId, token);
      expect({ name, result }).toEqual({ name, result: status });
    }

    // 3. check the player can still open it, so the attempts above were turned down for their token
    const player = await openChatConnection(PORT, chatId, createAuthToken(playerId));
    expect(typeof player).toBe('object');
  });
});
