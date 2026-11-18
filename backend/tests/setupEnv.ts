import fs from 'fs';
import path from 'path';

import dotenv from 'dotenv';

/*  Runs before every test file (setupFiles in jest.config.js).

    On the host, the MySQL credentials are only in the root .env (docker-compose passes them to
    the backend container), so they're loaded from there, and the tests connect to the port
    docker-compose.yml publishes MySQL on, on this machine only.

    Inside the backend container there is no root .env (it's above /app), so nothing changes and
    the tests use the credentials and mysql-db host docker-compose gives the container.
    Values already set in the environment always win.
 */
const rootEnv = path.resolve(__dirname, '..', '..', '.env');

if (fs.existsSync(rootEnv)) {
  dotenv.config({ path: rootEnv, quiet: true });
  process.env.MYSQL_HOST ??= '127.0.0.1';
  process.env.MYSQL_PORT ??= '3307';
}
