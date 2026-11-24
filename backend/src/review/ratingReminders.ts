import { logInfo } from '../core/logger';
import { sendPushToUsers } from '../core/notifications/push';
import { repeatEvery } from '../core/repeatEvery';
import { claimDueRatingReminders, getPlayersWithRatingsLeft, type ReminderGame } from './db/ratingReminders';
import { REMINDER_BEFORE_CLOSE_HOURS } from './ratingWindow';

// how often the server looks for games whose reminder is due
const CHECK_EVERY_MS = 60 * 1000;

// reminds the game's players who still have someone to rate
async function remindPlayers(game: ReminderGame): Promise<void> {
  const userIds = await getPlayersWithRatingsLeft(game.gameId);
  const devices = await sendPushToUsers(userIds, {
    title: 'Rating closes soon',
    body: `Rating for ${game.title} closes in ${REMINDER_BEFORE_CLOSE_HOURS} hour, rate your teammates before it does`,
    data: { type: 'rating_reminder', gameId: String(game.gameId) },
  });
  logInfo(`Rating reminder for game id: ${game.gameId} sent to ${devices} device(s)`);
}

/*  reminds the players of every game whose rating closes within REMINDER_BEFORE_CLOSE_HOURS,
    if they still have someone to rate. Each game is reminded once. Returns how many games
    were reminded
 */
export async function sendDueRatingReminders(): Promise<number> {
  const games = await claimDueRatingReminders();
  await Promise.all(games.map(remindPlayers));
  return games.length;
}

/*  looks for due reminders every CHECK_EVERY_MS while the server runs. Returns the function
    that stops it
 */
export function startRatingReminders(): () => void {
  return repeatEvery(CHECK_EVERY_MS, sendDueRatingReminders, 'Sending rating reminders failed:');
}
