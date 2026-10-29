import type { NextFunction, Request, Response } from 'express';
import { logError } from '../logger';

/*  an error a route didn't expect, e.g. the database is down. message is shown to
    the app, failure is the original error for the server log
 */
export class RouteError extends Error {
  constructor(message: string, readonly failure: unknown) {
    super(message);
  }
}

/*  wraps an async route handler or middleware so Express gets a plain function back.
    Anything it throws reaches handleRouteErrors, which answers 500 server_error with
    failureMessage, so routes don't need their own try/catch for unexpected errors
 */
export function asyncRoute<Req extends Request>(
  failureMessage: string,
  handler: (req: Req, res: Response, next: NextFunction) => Promise<unknown>,
): (req: Req, res: Response, next: NextFunction) => void {
  return (req, res, next) => {
    handler(req, res, next).catch((error: unknown) => {
      next(new RouteError(failureMessage, error));
    });
  };
}

/*  Express error handler for the routes: logs a RouteError and answers 500 server_error
    with its message. Other errors (e.g. a malformed json body) keep Express's own handling
 */
export function handleRouteErrors(error: unknown, req: Request, res: Response, next: NextFunction): void {
  if (!(error instanceof RouteError) || res.headersSent) {
    next(error);
    return;
  }
  logError(`${req.method} ${req.path} failed:`, error.failure);
  res.status(500).json({ msg: 'server_error', error: error.message });
}
