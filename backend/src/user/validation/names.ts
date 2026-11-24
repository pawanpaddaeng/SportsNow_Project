// the rules for a first or last name: how long it can be and which characters it can use

export const MAX_NAME_LENGTH = 100;

const ALLOWED_NAME_CHARACTER = /[\p{L}\p{M} '.-]/u;

export function illegalNameCharacters(name: string): string[] {
  const illegal = new Set<string>();
  for (const character of name) {
    if (!ALLOWED_NAME_CHARACTER.test(character)) illegal.add(character);
  }
  return [...illegal];
}

export function checkName(name: string, label: string): string | null {
  if (name.length > MAX_NAME_LENGTH) return `${label} must be at most ${MAX_NAME_LENGTH} characters`;
  const illegal = illegalNameCharacters(name);
  if (illegal.length > 0) return `${label} contains characters that aren't allowed: ${illegal.join(' ')}`;
  return null;
}
