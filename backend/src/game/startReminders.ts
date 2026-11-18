import { logError, logInfo } from '../core/logger';
import { notifyUsers } from '../core/notifications/notify';
import { claimDueStartReminders, getGamePlayerIds, type StartingGame } from './db/startReminders';

// how often the server looks for games whose reminder is due
const CHECK_EVERY_MS = 60 * 1000;

// "1 hour" when the reminder goes out on time, otherwise the minutes left, e.g. "25 minutes"
export function timeUntilStart(minutesLeft: number): string {
  if (minutesLeft >= 55) return '1 hour';
  return minutesLeft === 1 ? '1 minute' : `${minutesLeft} minutes`;
}

// reminds the game's organizer and players that it starts soon
async function remindPlayers(game: StartingGame): Promise<void> {
  const userIds = await getGamePlayerIds(game.gameId);
  const devices = await notifyUsers(userIds, {
    type: 'game_starting',
    title: 'Game starting soon',
    body: `${game.title} starts in ${timeUntilStart(game.minutesLeft)}`,
    gameId: game.gameId,
  });
  logInfo(`Start reminder for game id: ${game.gameId} sent to ${devices} device(s)`);
}

/*  reminds the organizer and players of every scheduled game starting within the hour.
    Each game is reminded once, or again if its start time moves. Returns how many games
    were reminded
 */
export async function sendDueStartReminders(): Promise<number> {
  const games = await claimDueStartReminders();
  await Promise.all(games.map(remindPlayers));
  return games.length;
}

/*  looks for due reminders every CHECK_EVERY_MS while the server runs. Returns the function
    that stops it
 */
export function startGameStartReminders(): () => void {
  const timer = setInterval(() => {
    sendDueStartReminders().catch((error: unknown) => {
      logError('Sending game start reminders failed:', error);
    });
  }, CHECK_EVERY_MS);
  return () => {
    clearInterval(timer);
  };
}
