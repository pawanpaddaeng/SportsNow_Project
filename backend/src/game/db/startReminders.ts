import { executeWrite, inTransaction, placeholders, selectRows } from '../../core/db/mysql';

// how long before a game starts its organizer and players are reminded
export const START_REMINDER_BEFORE_MINUTES = 60;

// a scheduled game starting within START_REMINDER_BEFORE_MINUTES
export interface StartingGame {
  gameId: number;
  title: string;
  // whole minutes until it starts, at least 1
  minutesLeft: number;
}

/*  scheduled games starting within START_REMINDER_BEFORE_MINUTES that haven't had their
    reminder yet, marked reminded as they're returned. The rows stay locked until they're
    marked, so a second check running at the same time can't return them too. Rows someone
    else has locked (e.g. a game being edited) are skipped instead of waited for, so the check
    can't deadlock with them, and are returned by the next check. A game made less than an
    hour before it starts is returned on the next check
 */
export async function claimDueStartReminders(): Promise<StartingGame[]> {
  return inTransaction(async (connection) => {
    const due = await selectRows<{ game_id: number; title: string; minutes_left: number }>(
      `SELECT game_id, title, TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), starts_at) AS minutes_left
       FROM games
       WHERE status = 'scheduled' AND start_reminder_sent = FALSE
         AND starts_at > UTC_TIMESTAMP()
         AND starts_at <= UTC_TIMESTAMP() + INTERVAL ${START_REMINDER_BEFORE_MINUTES} MINUTE
       FOR UPDATE SKIP LOCKED`,
      [],
      connection,
    );
    if (due.length > 0) {
      await executeWrite(
        `UPDATE games SET start_reminder_sent = TRUE WHERE game_id IN (${placeholders(due.length)})`,
        due.map((game) => game.game_id),
        connection,
      );
    }
    return due.map((game) => ({
      gameId: game.game_id,
      title: game.title,
      minutesLeft: Math.max(1, Number(game.minutes_left)),
    }));
  });
}

// everyone in the game, the organizer included
export async function getGamePlayerIds(gameId: number): Promise<number[]> {
  const rows = await selectRows<{ user_id: number }>(
    'SELECT user_id FROM game_players WHERE game_id = ?',
    [gameId],
  );
  return rows.map((row) => row.user_id);
}
