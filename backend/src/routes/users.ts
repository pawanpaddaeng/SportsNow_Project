import { Router, type Request, type Response } from 'express';
import { getAuthUser, requireAuth } from '../auth/middleware';
import { findUserById, updateUserProfile, userJson } from '../db/users';
import { asyncRoute } from '../http/asyncRoute';
import { parseProfileFields, type ProfileBody } from '../validation/profile';

// the signed in user's profile
export function usersRouter(): Router {
  const router = Router();

  /*GET profile
  */
  router.get('/api/users/me', requireAuth, (_req: Request, res: Response) => {
    const user = getAuthUser(res);
    return res.status(200).json({ msg: 'success', user: userJson(user)});
  });

  /*
  Update Profile
  */
  router.put('/api/users/me', requireAuth, asyncRoute('Could not update profile, try again later',
    async (req: Request<Record<string, never>, unknown, ProfileBody | undefined>, res: Response) => {
      const parsed = parseProfileFields(req.body);
      if(!parsed.ok) {
        return res.status(400).json({ msg: 'invalid_request', error: parsed.error });
      }

      const { userId } = getAuthUser(res);
      await updateUserProfile(userId, parsed.value);

      const updated = await findUserById(userId);
      if(!updated) {
        return res.status(404).json({ msg: 'no_user', error: 'No account found, sign up or sign in again' });
      }
      return res.status(200).json({ msg: 'success', user: userJson(updated) });
    }));

  return router;
}
