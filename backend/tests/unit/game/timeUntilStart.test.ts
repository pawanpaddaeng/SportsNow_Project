import { timeUntilStart } from '../../../src/game/startReminders';

// how a start reminder says how soon the game starts, from the whole minutes left
describe('timeUntilStart', () => {
  test.each([
    [60, '1 hour'],
    [55, '1 hour'],
    [54, '54 minutes'],
    [2, '2 minutes'],
    [1, '1 minute'],
  ])('%i minutes left reads "%s"', (minutesLeft, expected) => {
    expect(timeUntilStart(minutesLeft)).toBe(expected);
  });
});
