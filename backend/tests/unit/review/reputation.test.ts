import { gameScore, type PlayedGame, type RatingScores, reputationScore } from '../../../src/playerReview/reputation';

function stars(value: number): RatingScores {
  return { punctuality: value, sportsmanship: value, skill: value, reliability: value, overall: value };
}

// a game the player showed up to, with no ratings given unless the test says so
function played(raterCount: number, ratings: RatingScores[] = []): PlayedGame {
  return { noShow: false, raterCount, ratings };
}

const NO_SHOW: PlayedGame = { noShow: true, raterCount: 3, ratings: [] };

describe('gameScore', () => {
  test('a no-show scores 1 whatever ratings exist', () => {
    expect(gameScore({ ...NO_SHOW, ratings: [stars(5)] })).toBe(1);
  });

  test('anyone who did not rate counts as 5', () => {
    expect(gameScore(played(4))).toBe(5);
    expect(gameScore(played(2, [stars(3)]))).toBe(4);
  });

  test('every rating is averaged across its five categories', () => {
    const rating = { punctuality: 1, sportsmanship: 2, skill: 3, reliability: 4, overall: 5 };
    expect(gameScore(played(1, [rating]))).toBe(3);
    expect(gameScore(played(2, [rating, stars(1)]))).toBe(2);
  });

  test('nobody else showed up, so the game counts as 5', () => {
    expect(gameScore(played(0))).toBe(5);
  });
});

describe('reputationScore', () => {
  test('a new player starts at 5', () => {
    expect(reputationScore([])).toBe(5);
  });

  test('a new player has five perfect games behind them', () => {
    expect(reputationScore([NO_SHOW])).toBe(4.2);
  });

  test('only the five most recent games count', () => {
    const games = [played(1), played(1), played(1), played(1), played(1), NO_SHOW];
    expect(reputationScore(games)).toBe(5);
  });

  test('averages the games and rounds to two decimal places', () => {
    const games = [played(3, [stars(1)]), played(1), played(1), played(1), played(1)];
    expect(reputationScore(games)).toBe(4.73);
  });
});
