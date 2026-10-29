import type { Response } from 'express';
import {
  type CompleteGameProblem,
  type Game,
  type GameChangeProblem,
  getGameById,
  type LeaveGameProblem,
} from '../db/games';
import { invalidRequest } from '../../core/http/responses';
import { logError } from '../../core/logger';

// largest games.game_id (INT UNSIGNED)
const MAX_GAME_ID = 4294967295;

// notifications are best effort, a failure is only logged
export function logNotificationError(error: unknown): void {
  logError('Sending a notification failed:', error);
}

// the game id from a route like /api/games/:gameId, or null if it isn't a valid id
export function readGameId(raw: string): number | null {
  const gameId = /^\d{1,10}$/.test(raw) ? Number(raw) : NaN;
  return Number.isSafeInteger(gameId) && gameId >= 1 && gameId <= MAX_GAME_ID ? gameId : null;
}

// the route's game id, or null after answering 400 invalid_request when it isn't a valid id
export function requireGameId(raw: string, res: Response): number | null {
  const gameId = readGameId(raw);
  if (gameId === null) invalidRequest(res, 'gameId must be a positive whole number');
  return gameId;
}

// an error status with its json body, e.g. from gameChangeError
export interface ErrorResponse {
  status: number;
  body: { msg: string; error: string };
}

export function sendError(res: Response, error: ErrorResponse): Response {
  return res.status(error.status).json(error.body);
}

// the game read back after a change so the app gets the stored values
export async function readBackGame(gameId: number, change: string): Promise<Game> {
  const game = await getGameById(gameId);
  if (!game) {
    throw new Error(`Game ${gameId} not found after ${change}`);
  }
  return game;
}

/*  the response for a problem from one of the error tables. The tables are Records
    keyed by every problem, so the compiler makes sure each one has a response
 */
export function responseFor(responses: Record<string, ErrorResponse>, problem: string): ErrorResponse {
  const response = new Map(Object.entries(responses)).get(problem);
  if (!response) throw new Error(`No response for problem ${problem}`);
  return response;
}

// responses for a game the organizer can't change, shared by edit and cancel
export const GAME_CHANGE_ERRORS: Record<GameChangeProblem, ErrorResponse> = {
  not_found: { status: 404, body: { msg: 'not_found', error: 'This game does not exist' } },
  not_organizer: { status: 403, body: { msg: 'forbidden', error: 'Only the organizer can change this game' } },
  cancelled: { status: 409, body: { msg: 'game_cancelled', error: 'This game was cancelled' } },
  started: { status: 409, body: { msg: 'game_started', error: 'This game has already started' } },
};

export function gameChangeError(problem: GameChangeProblem): ErrorResponse {
  return responseFor(GAME_CHANGE_ERRORS, problem);
}

// responses for a game the organizer can't mark completed, the rest as for edits
export const COMPLETE_GAME_ERRORS: Record<CompleteGameProblem, ErrorResponse> = {
  not_found: GAME_CHANGE_ERRORS.not_found,
  not_organizer: GAME_CHANGE_ERRORS.not_organizer,
  cancelled: GAME_CHANGE_ERRORS.cancelled,
  not_started: { status: 409, body: { msg: 'game_not_started', error: "This game hasn't started yet" } },
  completed: { status: 409, body: { msg: 'game_completed', error: 'This game was already completed' } },
};

// responses for a game the user can't leave, a missing game as for edits
export const LEAVE_GAME_ERRORS: Record<LeaveGameProblem, ErrorResponse> = {
  not_found: GAME_CHANGE_ERRORS.not_found,
  not_player: { status: 409, body: { msg: 'not_player', error: "You're not in this game" } },
  organizer_must_cancel: {
    status: 409,
    body: { msg: 'organizer_must_cancel', error: 'You organize this game, cancel it before leaving' },
  },
  organizer_must_complete: {
    status: 409,
    body: { msg: 'organizer_must_complete', error: 'You organize this game, mark it completed before leaving' },
  },
};
