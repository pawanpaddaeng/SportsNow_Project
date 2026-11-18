import * as logger from '../../../src/core/logger';
import * as push from '../../../src/core/notifications/push';
import * as startRemindersDb from '../../../src/game/db/startReminders';
import { sendDueStartReminders, startGameStartReminders } from '../../../src/game/startReminders';
import * as notificationsDb from '../../../src/notification/db/notifications';
import type * as MockEnv from '../mockEnv';
import { cleanUpAfterTests, letNotificationsFinish } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

// the server looks for due reminders this often
const ONE_MINUTE_MS = 60 * 1000;

afterEach(() => {
  jest.useRealTimers();
});

cleanUpAfterTests();

// The reminder the server sends the organizer and players about an hour before a game starts
describe('Mocked: game start reminders', () => {
  // Mocked behavior: claimDueStartReminders returns game 42 starting in 60 minutes and game 43
  //   in 25, getGamePlayerIds says who is in each, and saveNotifications and sendPushToUser are replaced
  // Input: the server's check for due reminders runs
  // Expected behavior: everyone in each due game gets a game_starting notification for that
  //   game, saved for their Notifications screen and sent as a push
  // Expected output: 2, the number of games reminded, with "Sunday Soccer starts in 1 hour"
  //   and "Monday Hoops starts in 25 minutes"
  test('Everyone in each due game is reminded', async () => {
    jest.spyOn(startRemindersDb, 'claimDueStartReminders').mockResolvedValue([
      { gameId: 42, title: 'Sunday Soccer', minutesLeft: 60 },
      { gameId: 43, title: 'Monday Hoops', minutesLeft: 25 },
    ]);
    jest.spyOn(startRemindersDb, 'getGamePlayerIds')
      .mockImplementation((gameId) => Promise.resolve(gameId === 42 ? [1, 2] : [5]));
    const saveNotifications = jest.spyOn(notificationsDb, 'saveNotifications').mockResolvedValue();
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    const reminded = await sendDueStartReminders();

    expect(reminded).toBe(2);
    expect(saveNotifications).toHaveBeenCalledTimes(2);
    expect(saveNotifications).toHaveBeenCalledWith([1, 2], {
      type: 'game_starting',
      title: 'Game starting soon',
      body: 'Sunday Soccer starts in 1 hour',
      gameId: 42,
    });
    expect(saveNotifications).toHaveBeenCalledWith([5], expect.objectContaining({
      body: 'Monday Hoops starts in 25 minutes',
      gameId: 43,
    }));
    expect(sendPushToUser).toHaveBeenCalledTimes(3);
    expect(sendPushToUser).toHaveBeenCalledWith(1, {
      title: 'Game starting soon',
      body: 'Sunday Soccer starts in 1 hour',
      data: { type: 'game_starting', gameId: '42' },
    });
  });

  // Mocked behavior: claimDueStartReminders returns no games, as when every due game was
  //   already reminded
  // Input: the server's check for due reminders runs
  // Expected behavior: nothing is saved and nobody is notified
  // Expected output: 0
  test('Nothing is sent when no reminder is due', async () => {
    jest.spyOn(startRemindersDb, 'claimDueStartReminders').mockResolvedValue([]);
    const saveNotifications = jest.spyOn(notificationsDb, 'saveNotifications').mockResolvedValue();
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(0);

    const reminded = await sendDueStartReminders();

    expect(reminded).toBe(0);
    expect(saveNotifications).not.toHaveBeenCalled();
    expect(sendPushToUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: claimDueStartReminders returns game 42 with nobody in it, as when
  //   everyone left after it was claimed
  // Input: the server's check for due reminders runs
  // Expected behavior: the game counts as reminded but nothing is saved or sent
  // Expected output: 1
  test('A due game with nobody in it sends nothing', async () => {
    jest.spyOn(startRemindersDb, 'claimDueStartReminders').mockResolvedValue([
      { gameId: 42, title: 'Sunday Soccer', minutesLeft: 40 },
    ]);
    jest.spyOn(startRemindersDb, 'getGamePlayerIds').mockResolvedValue([]);
    const saveNotifications = jest.spyOn(notificationsDb, 'saveNotifications').mockResolvedValue();
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(0);

    const reminded = await sendDueStartReminders();

    expect(reminded).toBe(1);
    expect(saveNotifications).not.toHaveBeenCalled();
    expect(sendPushToUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: claimDueStartReminders returns game 42 with players 1 and 2,
  //   saveNotifications throws as if the database were down, sendPushToUser is replaced, and
  //   logError is watched
  // Input: the server's check for due reminders runs
  // Expected behavior: the failed save is logged and the push is still sent to both players
  // Expected output: 1, and "Saving a game_starting notification failed:" in the log
  test('A failed save is logged and the push is still sent', async () => {
    jest.spyOn(startRemindersDb, 'claimDueStartReminders').mockResolvedValue([
      { gameId: 42, title: 'Sunday Soccer', minutesLeft: 60 },
    ]);
    jest.spyOn(startRemindersDb, 'getGamePlayerIds').mockResolvedValue([1, 2]);
    const failure = new Error('database down');
    jest.spyOn(notificationsDb, 'saveNotifications').mockRejectedValue(failure);
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    const reminded = await sendDueStartReminders();
    await letNotificationsFinish();

    expect(reminded).toBe(1);
    expect(sendPushToUser).toHaveBeenCalledTimes(2);
    expect(logError).toHaveBeenCalledWith('Saving a game_starting notification failed:', failure);
  });

  // Mocked behavior: Jest's fake timers stand in for the clock, and claimDueStartReminders
  //   returns no games
  // Input: the reminders start, two minutes pass, they're stopped, then two more minutes pass
  // Expected behavior: the server looks for due reminders once a minute while they run, and
  //   never after they're stopped
  // Expected output: 2 checks
  test('The server checks every minute until it is stopped', async () => {
    jest.useFakeTimers();
    const claim = jest.spyOn(startRemindersDb, 'claimDueStartReminders').mockResolvedValue([]);

    const stop = startGameStartReminders();
    await jest.advanceTimersByTimeAsync(ONE_MINUTE_MS - 1);
    expect(claim).not.toHaveBeenCalled();
    await jest.advanceTimersByTimeAsync(ONE_MINUTE_MS + 1);
    expect(claim).toHaveBeenCalledTimes(2);

    stop();
    await jest.advanceTimersByTimeAsync(2 * ONE_MINUTE_MS);
    expect(claim).toHaveBeenCalledTimes(2);
  });

  // Mocked behavior: Jest's fake timers stand in for the clock, claimDueStartReminders throws
  //   the first time, as when the database is down, then returns no games
  // Input: the reminders start and two minutes pass
  // Expected behavior: the failed check is logged and doesn't stop the server, and the next
  //   check still runs
  // Expected output: the error "Sending game start reminders failed:" in the log, and 2 checks
  test('A failed check is logged and the next one still runs', async () => {
    jest.useFakeTimers();
    const failure = new Error('Forced error');
    const claim = jest.spyOn(startRemindersDb, 'claimDueStartReminders')
      .mockRejectedValueOnce(failure)
      .mockResolvedValue([]);
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    const stop = startGameStartReminders();
    await jest.advanceTimersByTimeAsync(2 * ONE_MINUTE_MS);
    stop();

    expect(logError).toHaveBeenCalledTimes(1);
    expect(logError).toHaveBeenCalledWith('Sending game start reminders failed:', failure);
    expect(claim).toHaveBeenCalledTimes(2);
  });
});
