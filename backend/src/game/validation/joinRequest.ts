import type { NewJoinRequest, PlayFrequency, RequestSkillLevel } from '../db/joinRequests';
import type { ParseResult } from '../../core/validation';

// values accepted from the app, matching the join request popup
export const REQUEST_SKILL_LEVELS: readonly RequestSkillLevel[] = ['beginner', 'intermediate', 'advanced', 'competitive'];
export const PLAY_FREQUENCIES: readonly PlayFrequency[] = ['rarely', 'monthly', 'weekly', 'several_per_week'];

// limit matching join_requests.message
export const MAX_MESSAGE_LENGTH = 200;

/*  body of POST /api/games/:gameId/join-requests. Fields are unknown until checked
    since they come straight from the request
 */
export interface JoinRequestBody {
  skill_level?: unknown;
  play_frequency?: unknown;
  message?: unknown;
}

function fail(error: string): { ok: false; error: string } {
  return { ok: false, error };
}

// a missing, null or blank optional field
function isAbsent(value: unknown): boolean {
  return value === undefined || value === null || (typeof value === 'string' && value.trim() === '');
}

/*  checks the popup's answers: skill_level is required, play_frequency and message
    are optional. Returns a description of the first problem found if anything is invalid
 */
export function parseJoinRequest(body: JoinRequestBody | undefined): ParseResult<NewJoinRequest> {
  const input = body ?? {};

  const skillLevel = input.skill_level;
  if (typeof skillLevel !== 'string' || !(REQUEST_SKILL_LEVELS as readonly string[]).includes(skillLevel)) {
    return fail(`skill_level must be one of: ${REQUEST_SKILL_LEVELS.join(', ')}`);
  }

  let playFrequency: PlayFrequency | null = null;
  if (!isAbsent(input.play_frequency)) {
    const value = input.play_frequency;
    if (typeof value !== 'string' || !(PLAY_FREQUENCIES as readonly string[]).includes(value)) {
      return fail(`play_frequency must be one of: ${PLAY_FREQUENCIES.join(', ')}`);
    }
    playFrequency = value as PlayFrequency;
  }

  let message: string | null = null;
  if (!isAbsent(input.message)) {
    if (typeof input.message !== 'string' || input.message.trim().length > MAX_MESSAGE_LENGTH) {
      return fail(`message must be text (max ${MAX_MESSAGE_LENGTH} characters)`);
    }
    message = input.message.trim();
  }

  return {
    ok: true,
    value: { skillLevel: skillLevel as RequestSkillLevel, playFrequency, message },
  };
}
