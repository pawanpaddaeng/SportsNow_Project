/*  the value at index in a row of the table. Every index editDistance reads is inside its row:
    the only read that could fall outside, beforePrevious at j - 2, is behind a swap check that
    can't pass before the second letter of both words
 */
function cell(row: number[], index: number): number {
  return row[index] as number;
}

/*  how many single letter changes turn a into b, swapping two neighbouring letters counts
    as one. At the first letter of either word charAt(-1) is '', which never equals a
    letter, so a swap is only counted from the second letter of both
 */
export function editDistance(a: string, b: string): number {
  let beforePrevious: number[] = [];
  let previous = Array.from({ length: b.length + 1 }, (_unused, index) => index);

  for (let i = 1; i <= a.length; i++) {
    const current = [i];
    for (let j = 1; j <= b.length; j++) {
      const replace = cell(previous, j - 1) + Number(a.charAt(i - 1) !== b.charAt(j - 1));
      let best = Math.min(cell(previous, j) + 1, cell(current, j - 1) + 1, replace);
      if (a.charAt(i - 1) === b.charAt(j - 2) && a.charAt(i - 2) === b.charAt(j - 1)) {
        best = Math.min(best, cell(beforePrevious, j - 2) + 1);
      }
      current.push(best);
    }
    beforePrevious = previous;
    previous = current;
  }
  return cell(previous, b.length);
}
