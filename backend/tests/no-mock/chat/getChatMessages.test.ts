import { MESSAGES_PAGE_SIZE } from '../../../src/chat/db/chats';
import { makeFriends } from '../friend/friendHelpers';
import { addGame, addPlayer, addUser, api, asUser, cleanUpAfterTests, setGameStatus, withToken } from '../helpers';
import { addMessage, addMessages, gameChatId, MISSING_CHAT_ID, removeFromGame, unfriend } from './chatHelpers';

// a message's time as the route returns it, UTC without milliseconds
const ISO_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;

let organizerId: number;
let playerId: number;
let gameId: number;
let chatId: number;

beforeEach(async () => {
  organizerId = await addUser('Olive');
  playerId = await addUser('Pat');
  gameId = await addGame(organizerId);
  await addPlayer(gameId, playerId);
  chatId = await gameChatId(gameId);
});

cleanUpAfterTests();

// the messages a reply lists, as their texts, in the order returned
function texts(body: { messages: { text: string }[] }): string[] {
  return body.messages.map((message) => message.text);
}

// Interface GET /api/chats/{chatId}/messages
describe('Unmocked: GET /api/chats/:chatId/messages', () => {
  // Input: no jwt_token, and a jwt_token that isn't a real token
  // Expected status code: 400 with no token, 401 with a bad token
  // Expected behavior: no messages are read
  // Expected output: { msg: "invalid_request" } and { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    const noToken = await api.get(`/api/chats/${chatId}/messages`);
    expect(noToken.status).toBe(400);
    expect(noToken.body).toMatchObject({ msg: 'invalid_request' });

    const badToken = await api.get(`/api/chats/${chatId}/messages`).set(withToken('not-a-real-token'));
    expect(badToken.status).toBe(401);
    expect(badToken.body).toEqual({ msg: 'invalid_token', error: 'Invalid jwt_token' });
  });

  // Input: a player of the game opens its chat, which has no messages yet
  // Expected status code: 200
  // Expected behavior: nothing is listed, the chat is open for writing
  // Expected output: { msg: "success", messages: [], is_closed: false }
  test('New game chat has no messages', async () => {
    const response = await api.get(`/api/chats/${chatId}/messages`).set(asUser(playerId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', messages: [], is_closed: false });
  });

  // Input: the organizer sends "Hi all", then the player sends "See you there", then the
  //   player opens the chat
  // Expected status code: 200
  // Expected behavior: both messages are listed, newest first, each with its sender's name
  // Expected output: { msg: "success", messages, is_closed: false }, each message
  //   { message_id, chat_id, sender: { user_id, first_name, last_name }, text, created_at }
  test('Player reads the game chat, newest first', async () => {
    // 1. two messages, the organizer's first
    const firstId = await addMessage(chatId, organizerId, 'Hi all');
    const secondId = await addMessage(chatId, playerId, 'See you there');

    // 2. read the chat through the interface
    const response = await api.get(`/api/chats/${chatId}/messages`).set(asUser(playerId));

    // 3. check the newest message comes first, with its sender
    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      msg: 'success',
      messages: [
        {
          message_id: secondId,
          chat_id: chatId,
          sender: { user_id: playerId, first_name: 'Pat', last_name: 'Tester' },
          text: 'See you there',
          created_at: expect.stringMatching(ISO_TIME),
        },
        {
          message_id: firstId,
          chat_id: chatId,
          sender: { user_id: organizerId, first_name: 'Olive', last_name: 'Tester' },
          text: 'Hi all',
          created_at: expect.stringMatching(ISO_TIME),
        },
      ],
      is_closed: false,
    });
  });

  // Input: a chat with 55 messages. The player reads the newest page, then ?before= the
  //   oldest message on it, then ?before= the very first message
  // Expected status code: 200 each time
  // Expected behavior: the first page holds the newest 50, the next page the 5 older ones,
  //   and there is nothing before the first message. No message is skipped or repeated
  // Expected output: messages 55 to 6, then 5 to 1, then []
  test('Older messages load a page at a time with before', async () => {
    // 1. more messages than fit on one page
    const ids = await addMessages(chatId, organizerId, MESSAGES_PAGE_SIZE + 5);

    // 2. the newest page
    const firstPage = await api.get(`/api/chats/${chatId}/messages`).set(asUser(playerId));
    expect(firstPage.status).toBe(200);
    expect(firstPage.body.messages).toHaveLength(MESSAGES_PAGE_SIZE);
    expect(texts(firstPage.body)[0]).toBe('Message 55');
    expect(texts(firstPage.body)[MESSAGES_PAGE_SIZE - 1]).toBe('Message 6');

    // 3. the page before the oldest message shown
    const oldestShown = firstPage.body.messages[MESSAGES_PAGE_SIZE - 1].message_id;
    const secondPage = await api.get(`/api/chats/${chatId}/messages?before=${oldestShown}`).set(asUser(playerId));
    expect(secondPage.status).toBe(200);
    expect(texts(secondPage.body)).toEqual(['Message 5', 'Message 4', 'Message 3', 'Message 2', 'Message 1']);
    expect(secondPage.body.is_closed).toBe(false);

    // 4. nothing is older than the first message
    const lastPage = await api.get(`/api/chats/${chatId}/messages?before=${ids[0]}`).set(asUser(playerId));
    expect(lastPage.status).toBe(200);
    expect(lastPage.body).toEqual({ msg: 'success', messages: [], is_closed: false });
  });

  // Input: two friends, each opens the chat they share, which has one message from each
  // Expected status code: 200 for both
  // Expected behavior: both friends see the same messages, a friend chat never closes
  // Expected output: { msg: "success", messages: ["Lunch?", "Hello"], is_closed: false } for both
  test('Both friends read their friend chat', async () => {
    const friendId = await addUser('Fern');
    const friendChatId = await makeFriends(playerId, friendId);
    await addMessage(friendChatId, playerId, 'Hello');
    await addMessage(friendChatId, friendId, 'Lunch?');

    for (const userId of [playerId, friendId]) {
      const response = await api.get(`/api/chats/${friendChatId}/messages`).set(asUser(userId));
      expect(response.status).toBe(200);
      expect(texts(response.body)).toEqual(['Lunch?', 'Hello']);
      expect(response.body.is_closed).toBe(false);
    }
  });

  // Input: a game with one message is completed, and another is cancelled. A player opens each chat
  // Expected status code: 200
  // Expected behavior: the chat of a game that ended can still be read, but is closed
  // Expected output: { msg: "success", messages: ["Good game"], is_closed: true } for each
  test('Chat of a completed or cancelled game is closed but readable', async () => {
    const cancelledGameId = await addGame(organizerId);
    await addPlayer(cancelledGameId, playerId);
    const cancelledChatId = await gameChatId(cancelledGameId);

    await addMessage(chatId, organizerId, 'Good game');
    await addMessage(cancelledChatId, organizerId, 'Good game');
    await setGameStatus(gameId, 'completed');
    await setGameStatus(cancelledGameId, 'cancelled');

    for (const closedChatId of [chatId, cancelledChatId]) {
      const response = await api.get(`/api/chats/${closedChatId}/messages`).set(asUser(playerId));
      expect(response.status).toBe(200);
      expect(texts(response.body)).toEqual(['Good game']);
      expect(response.body.is_closed).toBe(true);
    }
  });

  // Input: chatId "abc", "0", "-1", "1.5" and "99999999999" (above the largest id)
  // Expected status code: 400
  // Expected behavior: no messages are read
  // Expected output: { msg: "invalid_request", error: "chatId must be a positive whole number" }
  test('Rejects a chatId that is not a valid id', async () => {
    for (const badId of ['abc', '0', '-1', '1.5', '99999999999']) {
      const response = await api.get(`/api/chats/${badId}/messages`).set(asUser(playerId));
      expect(response.status).toBe(400);
      expect(response.body).toEqual({ msg: 'invalid_request', error: 'chatId must be a positive whole number' });
    }
  });

  // Input: ?before= "abc", "0", "-5" and empty
  // Expected status code: 400
  // Expected behavior: no messages are read
  // Expected output: { msg: "invalid_request", error: "before must be a positive whole number" }
  test('Rejects a before that is not a valid id', async () => {
    for (const badBefore of ['abc', '0', '-5', '']) {
      const response = await api.get(`/api/chats/${chatId}/messages?before=${badBefore}`).set(asUser(playerId));
      expect(response.status).toBe(400);
      expect(response.body).toEqual({ msg: 'invalid_request', error: 'before must be a positive whole number' });
    }
  });

  // Input: a chatId no chat has
  // Expected status code: 404
  // Expected behavior: no messages are read
  // Expected output: { msg: "chat_not_found", error: "This chat does not exist" }
  test('Chat does not exist', async () => {
    const response = await api.get(`/api/chats/${MISSING_CHAT_ID}/messages`).set(asUser(playerId));

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'chat_not_found', error: 'This chat does not exist' });
  });

  // Input: a user who never played the game opens its chat, which has a message
  // Expected status code: 403
  // Expected behavior: the messages aren't shown to someone outside the game
  // Expected output: { msg: "forbidden", error: "You can't open this chat" }
  test('User who is not a player cannot open the game chat', async () => {
    await addMessage(chatId, organizerId, 'Players only');
    const outsiderId = await addUser('Otto');

    const response = await api.get(`/api/chats/${chatId}/messages`).set(asUser(outsiderId));

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: "You can't open this chat" });
  });

  // Input: the player is taken out of the game, then opens its chat
  // Expected status code: 403
  // Expected behavior: a player who left or was removed loses the chat with the game
  // Expected output: { msg: "forbidden", error: "You can't open this chat" }
  test('Player who left the game cannot open its chat any more', async () => {
    await removeFromGame(gameId, playerId);

    const response = await api.get(`/api/chats/${chatId}/messages`).set(asUser(playerId));

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: "You can't open this chat" });
  });

  // Input: a stranger opens two friends' chat, then the friends stop being friends and
  //   one of them opens it
  // Expected status code: 403 both times
  // Expected behavior: only the two users, while they're friends, can open a friend chat
  // Expected output: { msg: "forbidden", error: "You can't open this chat" }
  test('Stranger or former friend cannot open a friend chat', async () => {
    const friendId = await addUser('Fern');
    const friendChatId = await makeFriends(playerId, friendId);

    const stranger = await api.get(`/api/chats/${friendChatId}/messages`).set(asUser(organizerId));
    expect(stranger.status).toBe(403);
    expect(stranger.body).toEqual({ msg: 'forbidden', error: "You can't open this chat" });

    await unfriend(playerId, friendId);
    const formerFriend = await api.get(`/api/chats/${friendChatId}/messages`).set(asUser(playerId));
    expect(formerFriend.status).toBe(403);
    expect(formerFriend.body).toEqual({ msg: 'forbidden', error: "You can't open this chat" });
  });
});
