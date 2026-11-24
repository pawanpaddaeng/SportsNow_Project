import { type BadgeProgress, inBadgeOrder, qualifyingBadges } from '../../../src/review/badges';

// a user who hasn't done anything yet, with overrides for what the test is about
function progress(overrides: Partial<BadgeProgress> = {}): BadgeProgress {
  return {
    gamesJoined: 0,
    gamesHosted: 0,
    friends: 0,
    ratingsGiven: 0,
    gamesPlayed: 0,
    ratingsReceived: 0,
    perfectRatings: 0,
    score: null,
    ...overrides,
  };
}

describe('qualifyingBadges', () => {
  test('a new user has no badges', () => {
    expect(qualifyingBadges(progress())).toEqual([]);
  });

  test('the getting started badges need one of each', () => {
    expect(qualifyingBadges(progress({ gamesJoined: 1, gamesHosted: 1, friends: 1, ratingsGiven: 1 })))
      .toEqual(['first_game', 'first_host', 'first_friend', 'first_rating']);
  });

  test('Regular needs 10 games played and Veteran 50', () => {
    expect(qualifyingBadges(progress({ gamesPlayed: 9 }))).toEqual([]);
    expect(qualifyingBadges(progress({ gamesPlayed: 10 }))).toEqual(['games_10']);
    expect(qualifyingBadges(progress({ gamesPlayed: 49 }))).toEqual(['games_10']);
    expect(qualifyingBadges(progress({ gamesPlayed: 50 }))).toEqual(['games_10', 'games_50']);
  });

  test('Social Butterfly needs 10 friends', () => {
    expect(qualifyingBadges(progress({ friends: 9 }))).toEqual(['first_friend']);
    expect(qualifyingBadges(progress({ friends: 10 }))).toEqual(['first_friend', 'friends_10']);
  });

  test('MVP needs a score of at least 5 from at least 10 ratings', () => {
    expect(qualifyingBadges(progress({ ratingsReceived: 10, score: 5 }))).toEqual(['mvp']);
    expect(qualifyingBadges(progress({ ratingsReceived: 10, score: 4.99 }))).toEqual([]);
    // a high score from too few ratings
    expect(qualifyingBadges(progress({ ratingsReceived: 9, score: 6 }))).toEqual([]);
    // not rated yet
    expect(qualifyingBadges(progress({ ratingsReceived: 10, score: null }))).toEqual([]);
  });

  test('Mega MVP needs 10 perfect ratings', () => {
    expect(qualifyingBadges(progress({ perfectRatings: 9 }))).toEqual([]);
    expect(qualifyingBadges(progress({ perfectRatings: 10 }))).toEqual(['mega_mvp']);
  });
});

describe('inBadgeOrder', () => {
  test('sorts badges into the order the app shows them and drops unknown ids', () => {
    expect(inBadgeOrder(['mega_mvp', 'retired_badge', 'first_game', 'games_10'])).toEqual(['first_game', 'games_10', 'mega_mvp']);
  });
});
