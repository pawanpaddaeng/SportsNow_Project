import 'dotenv/config';

function readPort(name: string, rawPort: string | undefined, defaultPort: number): number {
  const port =
    rawPort === undefined || rawPort === ''
      ? defaultPort
      : Number.parseInt(rawPort, 10);

  if (Number.isNaN(port) || port < 1 || port > 65535) {
    throw new Error(`Invalid ${name}: ${rawPort}`);
  }
  return port;
}

const port = readPort('PORT', process.env.PORT, 3000);
// the chat WebSocket, on the port docker-compose and Caddy send it to
const wsPort = readPort('WS_PORT', process.env.WS_PORT, 8080);

// the environment variables, looked up by name with get() instead of indexing process.env
const environment = new Map(Object.entries(process.env));

// MySQL credentials are defined once in the root .env and passed in by
// docker-compose; the connection URL is built from them here.
function requiredEnv(name: string): string {
  const value = environment.get(name);
  if (value === undefined || value === '') {
    throw new Error(`${name} is not set (see .env.example)`);
  }
  return value;
}

const mysqlUser = requiredEnv('MYSQL_USER');
const mysqlPassword = requiredEnv('MYSQL_PASSWORD');
const mysqlDatabase = requiredEnv('MYSQL_DATABASE');
// Compose service name on the private db network.
const mysqlHost = process.env.MYSQL_HOST ?? 'mysql-db';
const mysqlPort = process.env.MYSQL_PORT ?? '3306';

// Encoded so passwords containing @, :, / etc. don't break the URL.
const mysqlUrl =
  `mysql://${encodeURIComponent(mysqlUser)}:${encodeURIComponent(mysqlPassword)}` +
  `@${mysqlHost}:${mysqlPort}/${encodeURIComponent(mysqlDatabase)}`;

// Secret used to sign the JWTs given to the app after sign up / sign in.
const jwtSecret = requiredEnv('JWT_SECRET');

export const env = {
  port,
  wsPort,
  mysqlUrl,
  mysqlUser,
  mysqlPassword,
  mysqlDatabase,
  jwtSecret,
} as const;
