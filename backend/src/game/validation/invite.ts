import type { ParseResult } from '../../core/validation';

// largest users.user_id (INT UNSIGNED)
const MAX_USER_ID = 4294967295;

// most friends one invite can name, more than a game can hold (max_players is at most 100)
export const MAX_INVITES = 100;

/*  body of POST /api/games/:gameId/invites. Fields are unknown until checked
    since they come straight from the request
 */
export interface InviteBody {
  friend_ids?: unknown;
}

/*  checks friend_ids is a list of 1 to MAX_INVITES user ids and returns them, each
    once. Returns what's wrong otherwise
 */
export function parseInvite(body: InviteBody | undefined): ParseResult<number[]> {
  const error = `friend_ids must be a list of 1 to ${MAX_INVITES} user ids`;
  const raw: unknown = body?.friend_ids;
  if (!Array.isArray(raw) || raw.length === 0 || raw.length > MAX_INVITES) return { ok: false, error };

  const friendIds: number[] = [];
  for (const id of raw) {
    if (typeof id !== 'number' || !Number.isInteger(id) || id < 1 || id > MAX_USER_ID) return { ok: false, error };
    friendIds.push(id);
  }
  return { ok: true, value: [...new Set(friendIds)] };
}