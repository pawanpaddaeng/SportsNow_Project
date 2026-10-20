import { applicationDefault, getApps, initializeApp } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
import { deleteDeviceTokens, getDeviceTokens } from '../db/deviceTokens';

// Firebase errors meaning a token will never work again, so it can be deleted
const DEAD_TOKEN_ERRORS = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

/*  Firebase Admin reads the service account key from the file in
    GOOGLE_APPLICATION_CREDENTIALS. Set up on first use so the server still
    starts (and tests run) without the key
 */
function ensureFirebaseApp(): void {
  if (getApps().length === 0) {
    initializeApp({ credential: applicationDefault() });
  }
}

// whether Firebase answered a send with an error meaning the token will never work again
function isDeadTokenResponse(response: unknown): boolean {
  const code = (response as { error?: { code?: unknown } } | undefined)?.error?.code;
  return typeof code === 'string' && DEAD_TOKEN_ERRORS.has(code);
}

export interface PushMessage {
  title: string;
  body: string;
  // extra values for the app, e.g. { type: 'join_request', gameId: '42' }
  data?: Record<string, string>;
}

/*  sends a push notification to every device the user is signed into and
    removes tokens Firebase reports as dead. Returns how many devices it reached.
    Never throws: notifications are best effort, the database stays the source of truth
 */
export async function sendPushToUser(userId: number, message: PushMessage): Promise<number> {
  try {
    const tokens = await getDeviceTokens(userId);
    if (tokens.length === 0) return 0;

    ensureFirebaseApp();
    const result = await getMessaging().sendEachForMulticast({
      tokens,
      notification: { title: message.title, body: message.body },
      data: message.data ?? {},
    });

    // one response per token, in the same order
    const responses: unknown[] = result.responses;
    const deadTokens = tokens.filter((_token, i) => isDeadTokenResponse(responses.at(i)));
    await deleteDeviceTokens(deadTokens);

    return Number(result.successCount);
  } catch (error) {
    console.error(`Sending push notification to user ${userId} failed:`, error);
    return 0;
  }
}

// sends the same notification to each user, returns how many devices it reached. Never throws
export async function sendPushToUsers(userIds: number[], message: PushMessage): Promise<number> {
  const reached = await Promise.all(userIds.map((userId) => sendPushToUser(userId, message)));
  return reached.reduce((total, devices) => total + devices, 0);
}
