import express, { type Express } from 'express';
import { handleRouteErrors } from './core/http/asyncRoute';
import { gamesRouter } from './game/routes/gameRoutes';
import { joinRequestsRouter } from './game/routes/joinRequestRoutes';
import { authRouter } from './user/routes/authRoutes';
import { usersRouter } from './user/routes/userRoutes';

export function createApp(): Express {
  const app = express();
  app.use(express.json())

  app.use(authRouter());
  app.use(gamesRouter());
  app.use(joinRequestsRouter());
  app.use(usersRouter());

  //simple api to test connection health
  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  //standard api to route not existent endpoints
  app.use((_req, res) => {
    res.status(404).json({ error: 'Not Found' });
  });

  // unexpected errors in the routes become 500 server_error, after every route
  app.use(handleRouteErrors);

  return app;
}
