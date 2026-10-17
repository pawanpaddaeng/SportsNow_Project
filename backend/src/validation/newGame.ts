import type { NewGame, SkillLevel } from '../db/games';

// values accepted from the app, matching the Create Game screen
export const SPORTS = ['basketball', 'soccer', 'volleyball', 'tennis', 'other'] as const;
export const SKILL_LEVELS: readonly SkillLevel[] = ['any', 'beginner', 'intermediate', 'advanced'];

// limits matching the games table
const MAX_TITLE_LENGTH = 100;
const MAX_DESCRIPTION_LENGTH = 1000;
const MAX_LOCATION_TEXT_LENGTH = 255;
const MIN_PLAYERS = 2;
const MAX_PLAYERS = 100;

// a game can't start in the past (small allowance for clock differences) or too far ahead
const START_TIME_TOLERANCE_MS = 60 * 1000;
const MAX_DAYS_AHEAD = 365;

/*  body of POST /api/games. Times are ISO 8601 strings, e.g. "2026-10-18T02:00:00Z".
    Fields are unknown until checked since they come straight from the request
 */
export interface CreateGameBody {
  title?: unknown;
  sport?: unknown;
  description?: unknown;
  starts_at?: unknown;
  ends_at?: unknown;
  location_name?: unknown;
  address?: unknown;
  latitude?: unknown;
  longitude?: unknown;
  max_players?: unknown;
  skill_level?: unknown;
}

export type ParseResult<T> = { ok: true; value: T } | { ok: false; error: string };

function fail(error: string): { ok: false; error: string } {
  return { ok: false, error };
}

// trimmed non-empty string no longer than maxLength, or null
function readText(value: unknown, maxLength: number): string | null {
  if (typeof value !== 'string') return null;
  const text = value.trim();
  return text !== '' && text.length <= maxLength ? text : null;
}

function readDate(value: unknown): Date | null {
  if (typeof value !== 'string') return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function readNumberInRange(value: unknown, min: number, max: number): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max ? value : null;
}

/*  checks the request body and turns it into a game for organizerId.
    Returns a description of the first problem found if anything is invalid
 */
export function parseNewGame(body: CreateGameBody | undefined, organizerId: number): ParseResult<NewGame> {
  const input = body ?? {};

  const title = readText(input.title, MAX_TITLE_LENGTH);
  if (!title) return fail(`title is required (max ${MAX_TITLE_LENGTH} characters)`);

  const sport = typeof input.sport === 'string' ? input.sport.trim().toLowerCase() : '';
  if (!(SPORTS as readonly string[]).includes(sport)) {
    return fail(`sport must be one of: ${SPORTS.join(', ')}`);
  }

  let description: string | null = null;
  if (input.description !== undefined && input.description !== null && input.description !== '') {
    description = readText(input.description, MAX_DESCRIPTION_LENGTH);
    if (!description) return fail(`description must be text (max ${MAX_DESCRIPTION_LENGTH} characters)`);
  }

  const startsAt = readDate(input.starts_at);
  if (!startsAt) return fail('starts_at is required as an ISO 8601 time, e.g. 2026-10-18T02:00:00Z');
  const now = Date.now();
  if (startsAt.getTime() < now - START_TIME_TOLERANCE_MS) return fail('starts_at must be in the future');
  if (startsAt.getTime() > now + MAX_DAYS_AHEAD * 24 * 60 * 60 * 1000) {
    return fail(`starts_at must be within ${MAX_DAYS_AHEAD} days`);
  }

  let endsAt: Date | null = null;
  if (input.ends_at !== undefined && input.ends_at !== null) {
    endsAt = readDate(input.ends_at);
    if (!endsAt) return fail('ends_at must be an ISO 8601 time');
    if (endsAt.getTime() <= startsAt.getTime()) return fail('ends_at must be after starts_at');
  }

  const locationName = readText(input.location_name, MAX_LOCATION_TEXT_LENGTH);
  if (!locationName) return fail(`location_name is required (max ${MAX_LOCATION_TEXT_LENGTH} characters)`);
  const address = readText(input.address, MAX_LOCATION_TEXT_LENGTH);
  if (!address) return fail(`address is required (max ${MAX_LOCATION_TEXT_LENGTH} characters)`);

  const latitude = readNumberInRange(input.latitude, -90, 90);
  const longitude = readNumberInRange(input.longitude, -180, 180);
  if (latitude === null || longitude === null) {
    return fail('latitude (-90 to 90) and longitude (-180 to 180) are required numbers');
  }

  const maxPlayers = input.max_players;
  if (typeof maxPlayers !== 'number' || !Number.isInteger(maxPlayers) ||
      maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
    return fail(`max_players must be a whole number from ${MIN_PLAYERS} to ${MAX_PLAYERS}`);
  }

  // skill level is optional and defaults to "any"
  const skillLevel = input.skill_level === undefined ? 'any' : input.skill_level;
  if (typeof skillLevel !== 'string' || !(SKILL_LEVELS as readonly string[]).includes(skillLevel)) {
    return fail(`skill_level must be one of: ${SKILL_LEVELS.join(', ')}`);
  }

  return {
    ok: true,
    value: {
      organizerId,
      title,
      sport,
      description,
      startsAt,
      endsAt,
      locationName,
      address,
      latitude,
      longitude,
      maxPlayers,
      skillLevel: skillLevel as SkillLevel,
    },
  };
}
