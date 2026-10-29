import type { Response } from 'express';

// answers 400 invalid_request with what was wrong with the request
export function invalidRequest(res: Response, error: string): Response {
  return res.status(400).json({ msg: 'invalid_request', error });
}
