// result of checking a request body: the parsed value, or what was wrong with it
export type ParseResult<T> = { ok: true; value: T } | { ok: false; error: string };

// largest value of the INT UNSIGNED id columns
const MAX_ID = 4294967295;

// an id from a request, or null if it isn't a valid id
export function readId(raw: string): number | null {
  const id = /^\d{1,10}$/.test(raw) ? Number(raw) : NaN;
  return Number.isSafeInteger(id) && id >= 1 && id <= MAX_ID ? id : null;
}
