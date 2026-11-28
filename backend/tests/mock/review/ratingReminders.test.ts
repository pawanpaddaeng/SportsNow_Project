import * as logger from '../../../src/core/logger';
import * as ratingRemindersDb from '../../../src/review/db/ratingReminders';
import { startRatingReminders } from '../../../src/review/ratingReminders';
import { cleanUpAfterTests } from '../helpers';

// the server looks for due reminders this often
const ONE_MINUTE_MS = 60 * 1000;

afterEach(() => {
  jest.useRealTimers();
});

cleanUpAfterTests();

// The rating reminder the server sends an hour before rating a completed game closes
describe('Mocked: rating reminders', () => {
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
