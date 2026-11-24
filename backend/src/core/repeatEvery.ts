import { logError } from './logger';

/*  runs task every intervalMs while the server runs. A failed run is logged with
    failureMessage and the next one still happens. Returns the function that stops it
 */
export function repeatEvery(intervalMs: number, task: () => Promise<unknown>, failureMessage: string): () => void {
  const timer = setInterval(() => {
    task().catch((error: unknown) => {
      logError(failureMessage, error);
    });
  }, intervalMs);
  return () => {
    clearInterval(timer);
  };
}
