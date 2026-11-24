// which badges a player has earned. No database here so the rules are in one place

import { BASELINE_SCORE } from './reputation';

// every badge, in the order the app shows them
export const BADGES = [
  'first_game',
  'first_host',
  'first_friend',
  'first_rating',
  'games_10',
  'games_50',
  'friends_10',
  'mvp',
  'mega_mvp',
] as const;

export type BadgeId = typeof BADGES[number];

/*  what the badges are worked out from. score is the reputation score, null if they aren't
    rated yet or it wasn't needed (fewer than MVP_RATINGS ratings received)
 */
export interface BadgeProgress {
  // games organized by someone else that the user is a player in
  gamesJoined: number;
  // games the user created, whatever happened to them after
  gamesHosted: number;
  friends: number;
  // ratings the user gave other players
  ratingsGiven: number;
  // completed games the user showed up to
  gamesPlayed: number;
  ratingsReceived: number;
  // ratings received with every category at MAX_STARS
  perfectRatings: number;
  score: number | null;
}

export const REGULAR_GAMES = 10;
export const VETERAN_GAMES = 50;
export const SOCIAL_FRIENDS = 10;
// MVP needs this many ratings received, so a couple of good games can't earn it
export const MVP_RATINGS = 10;
// 5 is above average, so MVP is a score of at least this
export const MVP_SCORE = BASELINE_SCORE;
export const MEGA_MVP_PERFECT_RATINGS = 10;

// what earns each badge, in BADGES order
const RULES: [BadgeId, (progress: BadgeProgress) => boolean][] = [
  ['first_game', (p) => p.gamesJoined >= 1],
  ['first_host', (p) => p.gamesHosted >= 1],
  ['first_friend', (p) => p.friends >= 1],
  ['first_rating', (p) => p.ratingsGiven >= 1],
  ['games_10', (p) => p.gamesPlayed >= REGULAR_GAMES],
  ['games_50', (p) => p.gamesPlayed >= VETERAN_GAMES],
  ['friends_10', (p) => p.friends >= SOCIAL_FRIENDS],
  // not rated yet counts as 0, below MVP_SCORE
  ['mvp', (p) => p.ratingsReceived >= MVP_RATINGS && (p.score ?? 0) >= MVP_SCORE],
  ['mega_mvp', (p) => p.perfectRatings >= MEGA_MVP_PERFECT_RATINGS],
];

// the badges the progress qualifies for right now, in BADGES order
export function qualifyingBadges(progress: BadgeProgress): BadgeId[] {
  return RULES.filter(([, earns]) => earns(progress)).map(([badge]) => badge);
}

// the badges in BADGES order, dropping any id that isn't a badge (e.g. one since removed)
export function inBadgeOrder(ids: string[]): BadgeId[] {
  return BADGES.filter((badge) => ids.includes(badge));
}
