import { selectRows } from '../../../src/core/db/mysql';
import { sendDueStartReminders } from '../../../src/game/startReminders';
import {
  addCancelledGame,
  addGame,
  addPlayer,
  addStartedGame,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  validGameBody,
} from '../helpers';

/*  players are reminded about an hour before their game starts. The extra 30 seconds keep the
    whole minutes left the same however long the test takes to reach the check. Other test
    files' games can be due at the same time, since they share the database, so each test only
    checks its own games
 */
const ONE_MINUTE_MS = 60 * 1000;
const IN_30_MINUTES = 30 * ONE_MINUTE_MS + 30 * 1000;
const IN_59_MINUTES = 59 * ONE_MINUTE_MS + 30 * 1000;
const IN_45_MINUTES = 45 * ONE_MINUTE_MS + 30 * 1000;
const IN_2_HOURS = 120 * ONE_MINUTE_MS;

let organizerId: number;
let playerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  playerId = await addUser('Pete');
});

cleanUpAfterTests();

function startsIn(ms: number): Date {
  return new Date(Date.now() + ms);
}

// the body of every start reminder really saved for the user about the game, oldest first
async function remindersFor(userId: number, gameId: number): Promise<string[]> {
  const rows = await selectRows<{ body: string }>(
    `SELECT body FROM notifications
     WHERE user_id = ? AND game_id = ? AND type = 'game_starting' ORDER BY notification_id`,
    [userId, gameId],
  );
  return rows.map((row) => row.body);
}

// whether the game is really marked as reminded
async function wasReminded(gameId: number): Promise<boolean> {
  const rows = await selectRows<{ start_reminder_sent: number }>(
    'SELECT start_reminder_sent FROM games WHERE game_id = ?',
    [gameId],
  );
  return Number(rows[0]?.start_reminder_sent) === 1;
}

// The game start reminder the server checks for every minute
describe('Unmocked: game start reminders', () => {
  // Input: a game starting in 30 minutes with the organizer and Pete. The check runs twice
  // Expected behavior: the organizer and Pete each get one reminder about the game, saved for
  //   their Notifications screen. The game is marked reminded, so the second check sends nothing more
  // Expected output: the reminder "Evening Basketball starts in 30 minutes"
  test('The organizer and players are reminded once', async () => {
    const gameId = await addGame(organizerId, { startsAt: startsIn(IN_30_MINUTES) });
    await addPlayer(gameId, playerId);

    await sendDueStartReminders();
    await sendDueStartReminders();

    expect(await remindersFor(organizerId, gameId)).toEqual(['Evening Basketball starts in 30 minutes']);
    expect(await remindersFor(playerId, gameId)).toEqual(['Evening Basketball starts in 30 minutes']);
    expect(await wasReminded(gameId)).toBe(true);
  });

  // Input: a game starting in 59 minutes, as when the reminder goes out on time
  // Expected behavior: the reminder says it starts in 1 hour rather than the exact minutes
  // Expected output: the reminder "Evening Basketball starts in 1 hour"
  test('A reminder on time says 1 hour', async () => {
    const gameId = await addGame(organizerId, { startsAt: startsIn(IN_59_MINUTES) });

    await sendDueStartReminders();

    expect(await remindersFor(organizerId, gameId)).toEqual(['Evening Basketball starts in 1 hour']);
  });

  // Input: a game starting in 2 hours, a game that started yesterday, and a cancelled game
  //   starting in 30 minutes, each with Pete in it
  // Expected behavior: nobody is reminded and none of the games is marked reminded
  // Expected output: no reminder for any of the games
  test('Games not starting within the hour, started or cancelled are not reminded', async () => {
    const laterGameId = await addGame(organizerId, { startsAt: startsIn(IN_2_HOURS) });
    const startedGameId = await addStartedGame(organizerId);
    const cancelledGameId = await addCancelledGame(organizerId, { startsAt: startsIn(IN_30_MINUTES) });
    for (const gameId of [laterGameId, startedGameId, cancelledGameId]) {
      await addPlayer(gameId, playerId);
    }

    await sendDueStartReminders();

    for (const gameId of [laterGameId, startedGameId, cancelledGameId]) {
      expect(await remindersFor(organizerId, gameId)).toEqual([]);
      expect(await remindersFor(playerId, gameId)).toEqual([]);
      expect(await wasReminded(gameId)).toBe(false);
    }
  });

  // Input: a game starting in 30 minutes is reminded, then the organizer edits only its title
  //   through PUT /api/games/{gameId}, then moves it to start in 2 hours, then to start in 45 minutes
  // Expected behavior: the title edit keeps it reminded, so nothing more is sent. Moving the
  //   start time clears it, it isn't reminded while it starts in 2 hours, and it's reminded
  //   again once it starts within the hour
  // Expected output: the reminders "Evening Basketball starts in 30 minutes" then
  //   "New Title starts in 45 minutes"
  test('Moving the start time reminds the players again', async () => {
    // 1. the game is reminded
    const startsAt = startsIn(IN_30_MINUTES);
    const gameId = await addGame(organizerId, { startsAt });
    await sendDueStartReminders();

    // 2. the organizer only changes the title
    const sameTime = validGameBody({ starts_at: startsAt.toISOString() });
    const titleEdit = await api.put(`/api/games/${gameId}`).set(asUser(organizerId)).send(sameTime);
    expect(titleEdit.status).toBe(200);
    await sendDueStartReminders();
    expect(await wasReminded(gameId)).toBe(true);

    // 3. the organizer moves it to start in 2 hours
    const later = validGameBody({ starts_at: startsIn(IN_2_HOURS).toISOString() });
    expect((await api.put(`/api/games/${gameId}`).set(asUser(organizerId)).send(later)).status).toBe(200);
    await sendDueStartReminders();
    expect(await wasReminded(gameId)).toBe(false);

    // 4. the organizer moves it to start in 45 minutes
    const sooner = validGameBody({ starts_at: startsIn(IN_45_MINUTES).toISOString() });
    expect((await api.put(`/api/games/${gameId}`).set(asUser(organizerId)).send(sooner)).status).toBe(200);
    await sendDueStartReminders();

    expect(await remindersFor(organizerId, gameId)).toEqual([
      'Evening Basketball starts in 30 minutes',
      'New Title starts in 45 minutes',
    ]);
    expect(await wasReminded(gameId)).toBe(true);
  });
});
