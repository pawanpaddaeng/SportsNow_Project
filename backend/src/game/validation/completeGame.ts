import type { ParseResult } from '../../core/validation';

/*  body of POST /api/games/:gameId/complete. Fields are unknown until checked since
    they come straight from the request
 */
export interface CompleteGameBody {
  no_show_user_ids?: unknown;
}

/*  checks the players the organizer unticked: leaving the list out means everyone
    showed up. Returns each user id once, or a description of the problem
 */
export function parseCompleteGame(body: CompleteGameBody | undefined): ParseResult<number[]> {
  const userIds = body?.no_show_user_ids ?? [];
  if (!Array.isArray(userIds) || !userIds.every((id) => Number.isInteger(id) && Number(id) > 0)) {
    return { ok: false, error: 'no_show_user_ids must be a list of user ids' };
  }
  return { ok: true, value: [...new Set(userIds as number[])] };
}
