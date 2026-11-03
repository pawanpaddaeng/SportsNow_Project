// how far off a name can be and still count as similar: changes needed / length
const MAX_DIFFERENCE = 0.6;

// how well a name matches what was typed, lower is better
export interface NameMatch {
  // 0 same name, 1 name starts with it, 2 a word starts with it, 3 contains it, 4 only similar
  tier: number;
  // 0 to 1, how different the closest part of the name is
  difference: number;
}

// lowercase without accents, so "jose" finds "José"
function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

function cell(row: number[], index: number): number {
  return row.at(index) ?? 0;
}

// how many single letter changes turn a into b, swapping two neighbouring letters counts as one
function editDistance(a: string, b: string): number {
  let beforePrevious: number[] = [];
  let previous = Array.from({ length: b.length + 1 }, (_unused, index) => index);

  for (let i = 1; i <= a.length; i++) {
    const current = [i];
    for (let j = 1; j <= b.length; j++) {
      const replace = cell(previous, j - 1) + (a.charAt(i - 1) === b.charAt(j - 1) ? 0 : 1);
      let best = Math.min(cell(previous, j) + 1, cell(current, j - 1) + 1, replace);
      if (i > 1 && j > 1 && a.charAt(i - 1) === b.charAt(j - 2) && a.charAt(i - 2) === b.charAt(j - 1)) {
        best = Math.min(best, cell(beforePrevious, j - 2) + 1);
      }
      current.push(best);
    }
    beforePrevious = previous;
    previous = current;
  }
  return cell(previous, b.length);
}

function differenceBetween(query: string, part: string): number {
  return editDistance(query, part) / Math.max(query.length, part.length);
}

/*  compares what was typed with a full name and returns how well it matches, or null when
    it isn't similar. Compared with the whole name, each word, and the start of each, so
    "pwa" still finds "Pawan" while it is being typed
 */
export function matchName(typed: string, fullName: string): NameMatch | null {
  const query = normalize(typed);
  const name = normalize(fullName);
  if (query === '' || name === '') return null;

  if (name === query) return { tier: 0, difference: 0 };
  if (name.startsWith(query)) return { tier: 1, difference: 0 };
  const words = name.split(/\s+/);
  if (words.some((word) => word.startsWith(query))) return { tier: 2, difference: 0 };
  if (name.includes(query)) return { tier: 3, difference: 0 };

  const parts = [name, ...words];
  const startsOfParts = parts.map((part) => part.slice(0, query.length));
  const difference = Math.min(...[...parts, ...startsOfParts].map((part) => differenceBetween(query, part)));
  return difference <= MAX_DIFFERENCE ? { tier: 4, difference } : null;
}
