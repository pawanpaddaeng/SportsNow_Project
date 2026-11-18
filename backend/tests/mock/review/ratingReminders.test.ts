import * as logger from '../../../src/core/logger';
import * as push from '../../../src/core/notifications/push';
import * as ratingRemindersDb from '../../../src/review/db/ratingReminders';
import { sendDueRatingReminders, startRatingReminders } from '../../../src/review/ratingReminders';
import { cleanUpAfterTests } from '../helpers';

// the server looks for due reminders this often
const ONE_MINUTE_MS = 60 * 1000;

afterEach(() => {
  jest.useRealTimers();
});

cleanUpAfterTests();

// The rating reminder the server sends an hour before rating a completed game closes
describe('Mocked: rating reminders', () => {
  // Mocked behavior: claimDueRatingReminders returns games 42 and 43 as due,
  //   getPlayersWithRatingsLeft says who still has someone to rate, and sendPushToUsers is replaced
  // Input: the server's check for due reminders runs
  // Expected behavior: each due game's players with someone left to rate get one reminder
  //   that opens the game
  // Expected output: 2, the number of games reminded
  test('Players with someone left to rate are reminded', async () => {
    jest.spyOn(ratingRemindersDb, 'claimDueRatingReminders').mockResolvedValue([
      { gameId: 42, title: 'Sunday Soccer' },
      { gameId: 43, title: 'Monday Hoops' },
    ]);
    jest.spyOn(ratingRemindersDb, 'getPlayersWithRatingsLeft')
      .mockImplementation((gameId) => Promise.resolve(gameId === 42 ? [2, 3] : [5]));
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(1);

    const reminded = await sendDueRatingReminders();

    expect(reminded).toBe(2);
    expect(sendPushToUsers).toHaveBeenCalledTimes(2);
    expect(sendPushToUsers).toHaveBeenCalledWith([2, 3], {
      title: 'Rating closes soon',
      body: 'Rating for Sunday Soccer closes in 1 hour, rate your teammates before it does',
      data: { type: 'rating_reminder', gameId: '42' },
    });
    expect(sendPushToUsers).toHaveBeenCalledWith([5], expect.objectContaining({
      data: { type: 'rating_reminder', gameId: '43' },
    }));
  });

  // Mocked behavior: claimDueRatingReminders returns no games, as when every due game
  //   was already reminded
  // Input: the server's check for due reminders runs
  // Expected behavior: nobody is notified
  // Expected output: 0
  test('Nothing is sent when no reminder is due', async () => {
    jest.spyOn(ratingRemindersDb, 'claimDueRatingReminders').mockResolvedValue([]);
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(0);

    const reminded = await sendDueRatingReminders();

    expect(reminded).toBe(0);
    expect(sendPushToUsers).not.toHaveBeenCalled();
  });

  
  // Mocked behavior: Jest's fake timers stand in for the clock, and claimDueRatingReminders
  //   returns no games
  // Input: the reminders start, two minutes pass, they're stopped, then two more minutes pass
  // Expected behavior: the server looks for due reminders once a minute while they run, and
  //   never after they're stopped
  // Expected output: 2 checks
  test('The server checks every minute until it is stopped', async () => {
    jest.useFakeTimers();
    const claim = jest.spyOn(ratingRemindersDb, 'claimDueRatingReminders').mockResolvedValue([]);

    const stop = startRatingReminders();
    await jest.advanceTimersByTimeAsync(ONE_MINUTE_MS - 1);
    expect(claim).not.toHaveBeenCalled();
    await jest.advanceTimersByTimeAsync(ONE_MINUTE_MS + 1);
    expect(claim).toHaveBeenCalledTimes(2);

    stop();
    await jest.advanceTimersByTimeAsync(2 * ONE_MINUTE_MS);
    expect(claim).toHaveBeenCalledTimes(2);
  });

  
  // Mocked behavior: Jest's fake timers stand in for the clock, claimDueRatingReminders throws
  //   the first time, as when the database is down, then returns no games
  // Input: the reminders start and two minutes pass
  // Expected behavior: the failed check is logged and doesn't stop the server, and the next
  //   check still runs
  // Expected output: the error "Sending rating reminders failed:" in the log, and 2 checks
  test('A failed check is logged and the next one still runs', async () => {
    jest.useFakeTimers();
    const failure = new Error('Forced error');
    const claim = jest.spyOn(ratingRemindersDb, 'claimDueRatingReminders')
      .mockRejectedValueOnce(failure)
      .mockResolvedValue([]);
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    const stop = startRatingReminders();
    await jest.advanceTimersByTimeAsync(2 * ONE_MINUTE_MS);
    stop();

    expect(logError).toHaveBeenCalledTimes(1);
    expect(logError).toHaveBeenCalledWith('Sending rating reminders failed:', failure);
    expect(claim).toHaveBeenCalledTimes(2);
  });
});
