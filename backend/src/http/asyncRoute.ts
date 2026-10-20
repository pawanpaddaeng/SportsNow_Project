import type { NextFunction, Request, Response } from 'express';

/*  wraps an async route handler so Express gets a plain function back, and any
    error the handler throws goes to Express's error handler through next instead
    of becoming an unhandled promise rejection
 */
export function asyncRoute<Req extends Request>(
  handler: (req: Req, res: Response) => Promise<unknown>,
): (req: Req, res: Response, next: NextFunction) => void {
  return (req, res, next) => {
    handler(req, res).catch((error: unknown) => {
      next(error);
    });
  };
}
