// line breaks are replaced so a logged value can't start a fake log line (log forging)
function oneLine(message: string): string {
  return message.replace(/[\r\n]+/g, ' ');
}

// writes an info message to the server log on one line
export function logInfo(message: string): void {
  console.info(oneLine(message));
}

// writes an error message to the server log on one line, followed by the error itself
export function logError(message: string, error: unknown): void {
  console.error(oneLine(message), error);
}
