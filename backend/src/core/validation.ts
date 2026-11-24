// result of checking a request body: the parsed value, or what was wrong with it
export type ParseResult<T> = { ok: true; value: T } | { ok: false; error: string };

// largest value of the INT UNSIGNED id columns
const MAX_ID = 4294967295;

/*  the answer in a { decision } body: true for yes (e.g. "accept"), false for "decline",
    or null for anything else
 */
export function readDecision(decision: unknown, yes: string): boolean | null {
  if (decision === 'decline') return false;
  return decision === yes ? true : null;
}

// an id from a request, or null if it isn't a valid id
export function readId(raw: string): number | null {
  const id = /^\d{1,10}$/.test(raw) ? Number(raw) : NaN;
  return Number.isSafeInteger(id) && id >= 1 && id <= MAX_ID ? id : null;
}
