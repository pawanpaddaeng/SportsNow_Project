import { MAX_MESSAGE_LENGTH } from '../../../src/chat/validation/sendMessage';
import { executeWrite } from '../../../src/core/db/mysql';
import * as push from '../../../src/core/notifications/push';
import { createAuthToken } from '../../../src/user/auth/jwt';
import { connectToChat, openChatConnection, useChatServer, waitUntil } from '../../chatSocketClient';
import { makeFriends } from '../friend/friendHelpers';
import { addGame, addPlayer, addUser, cleanUpAfterTests, setGameStatus } from '../helpers';
import {
  addGameWithChat,
  gameChatId,
  getSavedMessages,
  ISO_TIME,
  MISSING_CHAT_ID,
  removeFromGame,
} from './chatHelpers';

// this file's own port, test files run at the same time
const PORT = 18101;
// the close code for a user who is no longer in the chat
const CLOSE_NOT_A_MEMBER = 4403;

let organizerId: number;
let playerId: number;
let gameId: number;
let chatId: number;

useChatServer(PORT);

beforeEach(async () => {
  ({ organizerId, playerId, gameId, chatId } = await addGameWithChat());
});

afterEach(() => {
  jest.restoreAllMocks();
});

cleanUpAfterTests();

// Interface WebSocket send_message (wss://DOMAIN/ws?chat_id=)
describe('Unmocked: WebSocket send_message', () => {
  // Input: connections with no chat_id, chat_id "abc", no token, a token that isn't real,
  //   a chat_id no chat has, and a user who isn't a player of the game
  // Expected status code: 400, 400, 400, 401, 404 and 403
  // Expected behavior: no connection is opened
  // Expected output: the HTTP status only, the connection never opens
  test('Rejects connections that may not open the chat', async () => {
    const outsiderId = await addUser('Otto');
    const token = createAuthToken(playerId);

    expect(await openChatConnection(PORT, null, token)).toBe(400);
    expect(await openChatConnection(PORT, 'abc', token)).toBe(400);
    expect(await openChatConnection(PORT, chatId, null)).toBe(400);
    expect(await openChatConnection(PORT, chatId, 'not-a-real-token')).toBe(401);
    expect(await openChatConnection(PORT, MISSING_CHAT_ID, token)).toBe(404);
    expect(await openChatConnection(PORT, chatId, createAuthToken(outsiderId))).toBe(403);
  });

  // Input: a plain HTTP request to the chat server, not a WebSocket
  // Expected status code: 426
  // Expected behavior: the server only accepts WebSocket connections
  // Expected output: an empty 426 Upgrade Required reply
  test('Plain HTTP request is answered 426', async () => {
    const response = await fetch(`http://127.0.0.1:${PORT}/`);

    expect(response.status).toBe(426);
  });

  // Input: the organizer and the player connect to the game chat, and the player is also
  //   connected to a second game's chat. The player sends { chat_id, text: "On my way",
  //   client_id: "c-1" }
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: the message is saved, and everyone connected to the chat gets it.
  //   The connection to the other chat gets nothing
  // Expected output: { type: "new_message", message: { message_id, chat_id, sender, text,
  //   created_at }, client_id: "c-1" } to both
  test('Message is saved and sent to everyone in the chat', async () => {
    // 1. both players connect, and the player also has another chat open
    const otherGameId = await addGame(organizerId);
    await addPlayer(otherGameId, playerId);
    const otherChat = await connectToChat(PORT, await gameChatId(otherGameId), createAuthToken(playerId));
    const organizer = await connectToChat(PORT, chatId, createAuthToken(organizerId));
    const player = await connectToChat(PORT, chatId, createAuthToken(playerId));

    // 2. the player sends a message
    player.sendMessage(chatId, 'On my way', 'c-1');

    // 3. check both get the saved message, with the sender's client_id
    const toPlayer = await player.nextReply();
    const toOrganizer = await organizer.nextReply();
    const saved = await getSavedMessages(chatId);
    expect(saved).toEqual([{ message_id: expect.any(Number), sender_id: playerId, text: 'On my way' }]);
    const expected = {
      type: 'new_message',
      message: {
        message_id: saved[0]?.message_id,
        chat_id: chatId,
        sender: { user_id: playerId, first_name: 'Pat', last_name: 'Tester' },
        text: 'On my way',
        created_at: expect.stringMatching(ISO_TIME),
      },
      client_id: 'c-1',
    };
    expect(toPlayer).toEqual(expected);
    expect(toOrganizer).toEqual(expected);

    // 4. the other chat's connection got nothing
    expect(await otherChat.unreadReplies()).toEqual([]);
  });

  // Input: { chat_id, text: "   Running late   " } with no client_id
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: the text is saved without its outer spaces
  // Expected output: new_message with text "Running late" and client_id null
  test('Outer spaces are trimmed and a missing client_id comes back null', async () => {
    const player = await connectToChat(PORT, chatId, createAuthToken(playerId));

    player.sendRaw(JSON.stringify({ type: 'send_message', chat_id: chatId, text: '   Running late   ' }));

    const reply = await player.nextReply();
    expect(reply).toMatchObject({ type: 'new_message', message: { text: 'Running late' }, client_id: null });
    expect((await getSavedMessages(chatId)).map((message) => message.text)).toEqual(['Running late']);
  });

  // Input: a text of exactly 1000 characters with spaces around it, then one of 1001
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: 1000 characters is the limit, the longer one is not saved and the
  //   connection stays open
  // Expected output: new_message for the first, then { type: "error", msg: "invalid_request",
  //   error: "text must be at most 1000 characters", client_id: null }
  test('Text of 1000 characters is sent, 1001 is rejected', async () => {
    const player = await connectToChat(PORT, chatId, createAuthToken(playerId));
    const longest = 'a'.repeat(MAX_MESSAGE_LENGTH);

    player.sendMessage(chatId, `  ${longest}  `, 'at-limit');
    expect(await player.nextReply()).toMatchObject({ type: 'new_message', message: { text: longest } });

    player.sendMessage(chatId, `${longest}a`, 'too-long');
    expect(await player.nextReply()).toEqual({
      type: 'error',
      msg: 'invalid_request',
      error: 'text must be at most 1000 characters',
      client_id: null,
    });
    expect(await getSavedMessages(chatId)).toHaveLength(1);
    expect(player.isOpen()).toBe(true);
  });

  // Input: a request that isn't JSON, one with type "typing", and one with text "   "
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: nothing is saved, the connection stays open for the next message
  // Expected output: { type: "error", msg: "invalid_request", error, client_id: null } for
  //   each, error naming what was wrong
  test('Bad requests are answered invalid_request', async () => {
    const player = await connectToChat(PORT, chatId, createAuthToken(playerId));

    player.sendRaw('hello');
    expect(await player.nextReply()).toEqual({
      type: 'error', msg: 'invalid_request', error: 'Request must be a JSON object', client_id: null,
    });

    player.sendRaw(JSON.stringify({ type: 'typing', chat_id: chatId, text: 'Hi' }));
    expect(await player.nextReply()).toEqual({
      type: 'error', msg: 'invalid_request', error: 'type must be send_message', client_id: null,
    });

    player.sendMessage(chatId, '   ', 'blank');
    expect(await player.nextReply()).toEqual({
      type: 'error', msg: 'invalid_request', error: 'text must not be empty', client_id: null,
    });

    expect(await getSavedMessages(chatId)).toEqual([]);
    expect(player.isOpen()).toBe(true);
  });

  // Input: the player sends a text message whose bytes (0xff, 0xfe) aren't valid UTF-8
  // Expected status code: none, it's a WebSocket connection
  // Expected behavior: the WebSocket library rejects it as a broken connection, the server
  //   logs the connection error and the connection is closed. Nothing is saved
  // Expected output: the connection closes with 1007 (invalid message data)
  test('A message that is not valid text closes the connection', async () => {
    const player = await connectToChat(PORT, chatId, createAuthToken(playerId));

    player.sendBytesAsText(Buffer.from([0xff, 0xfe]));

    expect((await player.closed).code).toBe(1007);
    expect(await getSavedMessages(chatId)).toEqual([]);
  });

  // Input: the player's connection is for the game chat, they send to another chat_id
  //   they are also a player of, with client_id "c-2"
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: a connection only sends to its own chat, nothing is saved in either
  // Expected output: { type: "error", msg: "forbidden", error: "This connection is for a
  //   different chat", client_id: "c-2" }
  test('Sending to a different chat than the connection is for', async () => {
    const otherGameId = await addGame(organizerId);
    await addPlayer(otherGameId, playerId);
    const otherChatId = await gameChatId(otherGameId);
    const player = await connectToChat(PORT, chatId, createAuthToken(playerId));

    player.sendMessage(otherChatId, 'Wrong chat', 'c-2');

    expect(await player.nextReply()).toEqual({
      type: 'error', msg: 'forbidden', error: 'This connection is for a different chat', client_id: 'c-2',
    });
    expect(await getSavedMessages(otherChatId)).toEqual([]);
    expect(await getSavedMessages(chatId)).toEqual([]);
    expect(player.isOpen()).toBe(true);
  });

  // Input: a completed game's chat is opened, and the game of an open connection is
  //   cancelled. A message is sent on each
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: a chat closed by its game ending can be opened and read but not
  //   written to, nothing is saved
  // Expected output: { type: "error", msg: "chat_closed", error: "This chat is closed
  //   because the game ended", client_id } for each
  test('Chat of a game that ended cannot be written to', async () => {
    // 1. a completed game's chat still opens
    await setGameStatus(gameId, 'completed');
    const completed = await connectToChat(PORT, chatId, createAuthToken(playerId));
    completed.sendMessage(chatId, 'Good game', 'done');
    expect(await completed.nextReply()).toEqual({
      type: 'error', msg: 'chat_closed', error: 'This chat is closed because the game ended', client_id: 'done',
    });

    // 2. a game cancelled while its chat is open
    const otherGameId = await addGame(organizerId);
    await addPlayer(otherGameId, playerId);
    const otherChatId = await gameChatId(otherGameId);
    const open = await connectToChat(PORT, otherChatId, createAuthToken(playerId));
    await setGameStatus(otherGameId, 'cancelled');
    open.sendMessage(otherChatId, 'Still on?', 'late');
    expect(await open.nextReply()).toEqual({
      type: 'error', msg: 'chat_closed', error: 'This chat is closed because the game ended', client_id: 'late',
    });

    expect(await getSavedMessages(chatId)).toEqual([]);
    expect(await getSavedMessages(otherChatId)).toEqual([]);
  });

  // Input: the player connects, is then taken out of the game, and sends a message
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: nothing is saved, and the server closes the connection with 4403
  // Expected output: { type: "error", msg: "forbidden", error: "You can't use this chat",
  //   client_id: "c-3" }, then the close
  test('Player removed while connected cannot send', async () => {
    const player = await connectToChat(PORT, chatId, createAuthToken(playerId));
    await removeFromGame(gameId, playerId);

    player.sendMessage(chatId, 'Am I still in?', 'c-3');

    expect(await player.nextReply()).toEqual({
      type: 'error', msg: 'forbidden', error: "You can't use this chat", client_id: 'c-3',
    });
    expect(await player.closed).toEqual({ code: CLOSE_NOT_A_MEMBER, reason: 'Not a member of this chat' });
    expect(await getSavedMessages(chatId)).toEqual([]);
  });

  // Input: both are connected, the player is taken out of the game, then the organizer sends
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: the organizer's message is saved and sent to them, the removed player
  //   doesn't get it and their connection is closed with 4403
  // Expected output: new_message to the organizer, nothing but the close to the player
  test('Removed player is disconnected instead of getting new messages', async () => {
    const organizer = await connectToChat(PORT, chatId, createAuthToken(organizerId));
    const player = await connectToChat(PORT, chatId, createAuthToken(playerId));
    await removeFromGame(gameId, playerId);

    organizer.sendMessage(chatId, 'Players only now', 'o-1');

    expect(await organizer.nextReply()).toMatchObject({ type: 'new_message', client_id: 'o-1' });
    expect(await player.closed).toEqual({ code: CLOSE_NOT_A_MEMBER, reason: 'Not a member of this chat' });
    expect(await player.unreadReplies(0)).toEqual([]);
  });

  // Input: two friends' chat is deleted while one of them has it open (as when the other
  //   deletes their account), then they send to it
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: nothing is saved, the connection is closed with 4403
  // Expected output: { type: "error", msg: "chat_not_found", error: "This chat does not
  //   exist", client_id: "c-4" }, then the close
  test('Chat deleted while connected', async () => {
    const friendId = await addUser('Fern');
    const friendChatId = await makeFriends(playerId, friendId);
    const player = await connectToChat(PORT, friendChatId, createAuthToken(playerId));
    await executeWrite('DELETE FROM chats WHERE chat_id = ?', [friendChatId]);

    player.sendMessage(friendChatId, 'Hello?', 'c-4');

    expect(await player.nextReply()).toEqual({
      type: 'error', msg: 'chat_not_found', error: 'This chat does not exist', client_id: 'c-4',
    });
    expect(await player.closed).toEqual({ code: CLOSE_NOT_A_MEMBER, reason: 'This chat does not exist' });
  });

  // Input: in a friend chat, the player sends "Lunch?" while their friend doesn't have the
  //   chat open, then again once the friend has it open
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: the friend is sent a notification for the first message only, the
  //   second reached them over the WebSocket. The test users have no devices, so no
  //   notification leaves the server
  // Expected output: sendPushToUsers([friend], { title: "Pat", body: "Lunch?", data:
  //   { type: "friend_message", chatId, friendName: "Pat Tester" }, tag: "friend-<Pat's id>" }),
  //   then sendPushToUsers([], ...)
  test('Friend is notified of a message unless they have the chat open', async () => {
    // 1. a friend chat, the friend isn't connected
    const friendId = await addUser('Fern');
    const friendChatId = await makeFriends(playerId, friendId);

    // 2. the friend is notified of the first message only
    await expectPushOnlyWhileAway({ chatId: friendChatId, senderId: playerId, recipientId: friendId, text: 'Lunch?' }, {
      title: 'Pat',
      body: 'Lunch?',
      data: { type: 'friend_message', chatId: String(friendChatId), friendName: 'Pat Tester' },
      tag: `friend-${playerId}`,
    });
  });

  // Input: in the game chat, the player sends "Bringing a ball" while the organizer doesn't
  //   have the chat open, then again once the organizer has it open
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: the organizer is sent a notification for the first message only, the
  //   second reached them over the WebSocket. The test users have no devices, so no
  //   notification leaves the server
  // Expected output: sendPushToUsers([organizer], { title: "Evening Basketball", body:
  //   "Pat: Bringing a ball", data: { type: "game_message", gameId }, tag: "game-<gameId>" }),
  //   then sendPushToUsers([], ...)
  test('Game players are notified of a message unless they have the chat open', async () => {
    // the game's chat, the organizer isn't connected and is notified of the first message only
    await expectPushOnlyWhileAway({ chatId, senderId: playerId, recipientId: organizerId, text: 'Bringing a ball' }, {
      title: 'Evening Basketball',
      body: 'Pat: Bringing a ball',
      data: { type: 'game_message', gameId: String(gameId) },
      tag: `game-${gameId}`,
    });
  });
});

// a message sent twice in a chat: first while the recipient doesn't have it open, then once they do
interface AwayMessage {
  chatId: number;
  senderId: number;
  recipientId: number;
  text: string;
}

/*  the sender sends the message while the recipient doesn't have the chat open, which must
    push notification to them, then again once they have it open, which must push to nobody
    since it reached them over the WebSocket. sendPushToUsers is watched without replacing
    it, the test users have no devices
 */
async function expectPushOnlyWhileAway(message: AwayMessage, notification: push.PushMessage): Promise<void> {
  const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers');
  const sender = await connectToChat(PORT, message.chatId, createAuthToken(message.senderId));

  sender.sendMessage(message.chatId, message.text, 'away');
  expect(await sender.nextReply()).toMatchObject({ type: 'new_message', client_id: 'away' });
  await waitUntil(() => sendPushToUsers.mock.calls.length === 1);
  expect(sendPushToUsers).toHaveBeenCalledWith([message.recipientId], notification);

  const recipient = await connectToChat(PORT, message.chatId, createAuthToken(message.recipientId));
  sender.sendMessage(message.chatId, message.text, 'open');
  expect(await recipient.nextReply()).toMatchObject({ type: 'new_message', client_id: 'open' });
  await waitUntil(() => sendPushToUsers.mock.calls.length === 2);
  expect(sendPushToUsers).toHaveBeenCalledTimes(2);
  expect(sendPushToUsers).toHaveBeenLastCalledWith([], notification);
}
