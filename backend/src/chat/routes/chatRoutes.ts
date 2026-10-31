import { Router, type Request, type Response } from 'express';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { readId } from '../../core/validation';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import { type ChatMessage, getChatAccess, getChatMessages } from '../db/chats';

function chatMessageJson(message: ChatMessage) {
  return {
    message_id: message.messageId,
    chat_id: message.chatId,
    sender: {
      user_id: message.senderId,
      first_name: message.senderFirstName,
      last_name: message.senderLastName,
    },
    text: message.text,
    created_at: message.createdAt,
  };
}

// loading a chat's earlier messages. New messages are sent over the WebSocket
export function chatsRouter(): Router {
  const router = Router();

  /*  a chat's messages, newest first, a page at a time. ?before= is the oldest message_id
      the app has, leave it out for the newest page, and a page with no messages means
      there are no older ones. is_closed says the chat can be read but not written to.
      Errors: 400 invalid_request, 403 forbidden (not a player of the game), 404 chat_not_found
   */
  router.get('/api/chats/:chatId/messages', requireAuth, asyncRoute('Could not load messages, try again later',
    async (req: Request<{ chatId: string }>, res: Response) => {
      const user = getAuthUser(res);
      const chatId = readId(req.params.chatId);
      if (chatId === null) return invalidRequest(res, 'chatId must be a positive whole number');

      const rawBefore = req.query.before;
      const before = rawBefore === undefined ? null : readId(String(rawBefore));
      if (rawBefore !== undefined && before === null) {
        return invalidRequest(res, 'before must be a positive whole number');
      }

      const access = await getChatAccess(chatId, user.userId);
      if (!access) return res.status(404).json({ msg: 'chat_not_found', error: 'This chat does not exist' });
      if (!access.isPlayer) {
        return res.status(403).json({ msg: 'forbidden', error: 'Only players of this game can open its chat' });
      }

      const messages = await getChatMessages(chatId, before);
      return res.status(200).json({
        msg: 'success',
        messages: messages.map(chatMessageJson),
        is_closed: access.isClosed,
      });
    }));

  return router;
}
