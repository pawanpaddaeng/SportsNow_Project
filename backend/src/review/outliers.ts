// how much an unusual game counts toward a player's score. No database here so the rules are in one place

/*  once a player showed up to MIN_GAMES_FOR_OUTLIERS games, a game scoring more than
    OUTLIER_DISTANCE stars from the middle of their game scores counts OUTLIER_WEIGHT as
    much as the others
 */
export const MIN_GAMES_FOR_OUTLIERS = 4;
export const OUTLIER_DISTANCE = 1.5;
export const OUTLIER_WEIGHT = 0.25;

// a game that counts toward the score, and what it scored
export interface CountedGame {
  noShow: boolean;
  stars: number;
}

// a game's score and how much it counts
export interface WeightedGame {
  stars: number;
  weight: number;
}

// the middle value, or the average of the two middle values when there's an even number
function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const middles = sorted.slice(Math.floor((sorted.length - 1) / 2), Math.floor(sorted.length / 2) + 1);
  return middles.reduce((sum, value) => sum + value, 0) / middles.length;
}

// the middle of the games they showed up to, or null while there are too few to tell what's unusual
function showedUpMiddle(games: CountedGame[]): number | null {
  const showedUp = games.filter((game) => !game.noShow).map((game) => game.stars);
  return showedUp.length >= MIN_GAMES_FOR_OUTLIERS ? median(showedUp) : null;
}

// a game far from the middle of their games. A no-show is never one, it always counts in full
function isOutlier(game: CountedGame, middle: number | null): boolean {
  if (game.noShow || middle === null) return false;
  return Math.abs(game.stars - middle) > OUTLIER_DISTANCE;
}

// every game with its weight: OUTLIER_WEIGHT for an unusual game, 1 for the others
export function weighOutliers(games: CountedGame[]): WeightedGame[] {
  const middle = showedUpMiddle(games);
  return games.map((game) => ({ stars: game.stars, weight: isOutlier(game, middle) ? OUTLIER_WEIGHT : 1 }));
}
