import { createApp } from './app';
import { env } from './config/env';
import { logInfo } from './logger';

const app = createApp();

const server = app.listen(env.port, () => {
  logInfo(`Server listening on port ${env.port}`);
});

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    server.close(() => {
      process.exit(0);
    });
  });
}
