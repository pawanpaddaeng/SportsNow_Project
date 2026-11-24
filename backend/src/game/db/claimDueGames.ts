import { executeWrite, inTransaction, placeholders, selectRows } from '../../core/db/mysql';

// the games columns that record a reminder was sent
export type ReminderSentColumn = 'start_reminder_sent' | 'rating_reminder_sent';

/*  the games selectSql returns, with sentColumn set as they're returned. selectSql locks
    the rows it reads (FOR UPDATE) so they stay locked until they're marked, and a second
    check running at the same time can't return them too
 */
export async function claimDueGames<Row extends { game_id: number }>(
  selectSql: string,
  sentColumn: ReminderSentColumn,
): Promise<Row[]> {
  return inTransaction(async (connection) => {
    const due = await selectRows<Row>(selectSql, [], connection);
    if (due.length > 0) {
      await executeWrite(
        `UPDATE games SET ${sentColumn} = TRUE WHERE game_id IN (${placeholders(due.length)})`,
        due.map((game) => game.game_id),
        connection,
      );
    }
    return due;
  });
}
