import { executeWrite, selectRows } from './mysql';

// longest FCM token accepted, matches the device_tokens.token column
export const MAX_FCM_TOKEN_LENGTH = 512;

/*  saves a device's FCM token for a user. Registering the same token again
    (e.g. on every app launch) only refreshes updated_at, and a token registered
    by a different user (shared device) moves to that user
 */
export async function saveDeviceToken(token: string, userId: number): Promise<void> {
  await executeWrite(
    `INSERT INTO device_tokens (token, user_id) VALUES (?, ?) AS new
     ON DUPLICATE KEY UPDATE user_id = new.user_id, updated_at = CURRENT_TIMESTAMP`,
    [token, userId],
  );
}

interface TokenRow {
  token: string;
}

// every device token registered for a user (one per device they're signed into)
export async function getDeviceTokens(userId: number): Promise<string[]> {
  const rows = await selectRows<TokenRow>(
    'SELECT token FROM device_tokens WHERE user_id = ?',
    [userId],
  );
  return rows.map((row) => row.token);
}

// removes tokens Firebase reported as no longer valid (app uninstalled, data cleared, ...)
export async function deleteDeviceTokens(tokens: string[]): Promise<void> {
  if (tokens.length === 0) return;
  const placeholders = tokens.map(() => '?').join(', ');
  await executeWrite(
    `DELETE FROM device_tokens WHERE token IN (${placeholders})`,
    tokens,
  );
}
