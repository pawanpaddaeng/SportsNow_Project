// result of checking a request body: the parsed value, or what was wrong with it
export type ParseResult<T> = { ok: true; value: T } | { ok: false; error: string };
