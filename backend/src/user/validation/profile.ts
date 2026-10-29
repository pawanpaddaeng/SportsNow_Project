import type { ParseResult } from '../../core/validation';

export const MIN_AGE = 19;
export const MAX_AGE = 100;
export const MAX_NAME_LENGTH = 100;

const ALLOWED_NAME_CHARACTER = /[\p{L}\p{M} '.-]/u;

export interface ProfileFields {
  firstName: string;
  lastName: string;
  birthYear: number;
}

export interface ProfileBody {
  f_name?: unknown;
  l_name?: unknown;
  birthYear?: unknown;
}

function fail(error: string): { ok: false; error: string } {
  return { ok: false, error };
}

function readName(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

export function illegalNameCharacters(name: string): string[] {
  const illegal = new Set<string>();
  for (const character of name) {
    if (!ALLOWED_NAME_CHARACTER.test(character)) illegal.add(character);
  }
  return [...illegal];
}

function checkName(name: string, label: string): string | null {
  if (name.length > MAX_NAME_LENGTH) return `${label} must be at most ${MAX_NAME_LENGTH} characters`;
  const illegal = illegalNameCharacters(name);
  if (illegal.length > 0) return `${label} contains characters that aren't allowed: ${illegal.join(' ')}`;
  return null;
}

export function parseProfileFields(body: ProfileBody | undefined): ParseResult<ProfileFields> {
  const input = body ?? {};
  const firstName = readName(input.f_name);
  const lastName = readName(input.l_name);
  const rawYear = input.birthYear;
  const hasYear = typeof rawYear === 'number' || (typeof rawYear === 'string' && rawYear.trim() !== '');

  if (!firstName || !lastName || !hasYear) {
    return fail('First Name, Last Name and Birth Year fields are compulsory');
  }

  const nameError = checkName(firstName, 'First Name') ?? checkName(lastName, 'Last Name');
  if (nameError) return fail(nameError);

  const birthYear = Number(rawYear);
  const currentYear = new Date().getFullYear();
  if (!Number.isInteger(birthYear) || birthYear > currentYear - MIN_AGE || birthYear < currentYear - MAX_AGE) {
    return fail(`Birth Year must be between ${currentYear - MAX_AGE} and ${currentYear - MIN_AGE}`);
  }

  return { ok: true, value: { firstName, lastName, birthYear } };
}