import { createApp } from './app';
import { env } from './config/env';
import { logInfo } from './logger';

const app = createApp();

const server = app.listen(env.port, () => {
  logInfo(`Server listening on port ${env.port}`);
});

// stop taking requests, then send the signal again: the listener is gone (once),
// so Node handles it the default way and the process ends
for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.once(signal, () => {
    server.close(() => {
      process.kill(process.pid, signal);
    });
  });
}
