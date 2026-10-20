import { executeWrite, selectRows } from './mysql';

export interface User {
  userId: number;
  googleId: string;
  email: string;
  firstName: string;
  lastName: string;
  birthYear: number;
  // when the account was created, compared with a JWT's iat to reject tokens issued before it
  createdAt: Date;
}

// fields needed to add a user, the id and creation time are set when it is added
export type NewUser = Omit<User, 'userId' | 'createdAt'>;

// a row of the users table as mysql2 returns it
interface UserRow {
  user_id: number;
  google_id: string;
  email: string;
  first_name: string;
  last_name: string;
  birth_year: number;
  // selected as UNIX_TIMESTAMP(created_at) so time zones don't affect it
  created_at_unix: number;
}

function toUser(row: UserRow): User {
  return {
    userId: row.user_id,
    googleId: row.google_id,
    email: row.email,
    firstName: row.first_name,
    lastName: row.last_name,
    birthYear: row.birth_year,
    createdAt: new Date(row.created_at_unix * 1000),
  };
}


/*  adds a user and returns their new user_id, throws ER_DUP_ENTRY if the google_id already exists.
    created_at comes from this server's clock (the same clock that sets JWT iat) so a token
    issued right after sign up is never older than the account
 */
export async function createUser(user: NewUser): Promise<number> {
  const createdAtSeconds = Math.floor(Date.now() / 1000);
  const result = await executeWrite(
    `INSERT INTO users (google_id, email, first_name, last_name, birth_year, created_at)
     VALUES (?, ?, ?, ?, ?, FROM_UNIXTIME(?))`,
    [user.googleId, user.email, user.firstName, user.lastName, user.birthYear, createdAtSeconds],
  );
  return result.insertId;
}

export async function findUserByGoogleId(googleId: string): Promise<User | null> {
  const rows = await selectRows<UserRow>(
    `SELECT user_id, google_id, email, first_name, last_name, birth_year,
            UNIX_TIMESTAMP(created_at) AS created_at_unix
     FROM users WHERE google_id = ? LIMIT 1`,
    [googleId],
  );
  const row = rows[0];
  return row ? toUser(row) : null;
}

export async function findUserById(userId: number): Promise<User | null> {
  const rows = await selectRows<UserRow>(
    `SELECT user_id, google_id, email, first_name, last_name, birth_year,
            UNIX_TIMESTAMP(created_at) AS created_at_unix
     FROM users WHERE user_id = ? LIMIT 1`,
    [userId],
  );
  const row = rows[0];
  return row ? toUser(row) : null;
}

export type ProfileUpdate = Pick<User, 'firstName' | 'lastName' | 'birthYear'>;

export async function updateUserProfile(userId: number, profile: ProfileUpdate): Promise<void> {
  await executeWrite(
    'UPDATE users SET first_name = ?, last_name = ?, birth_year = ? WHERE user_id = ?',
    [profile.firstName, profile.lastName, profile.birthYear, userId],
  );
}

export function userJson(user: User) {
  return {
    user_id: user.userId,
    email: user.email,
    f_name: user.firstName,
    l_name: user.lastName,
    birthYear: user.birthYear,
    created_at: user.createdAt.toISOString(),
  };
}

export async function userExists(googleId: string): Promise<boolean> {
  const rows = await selectRows<{ found: number }>(
    'SELECT 1 AS found FROM users WHERE google_id = ? LIMIT 1',
    [googleId],
  );
  return rows.length > 0;
}

// true when a query failed because a unique key (e.g. google_id) already exists
export function isDuplicateEntryError(error: unknown): boolean {
  return (error as { code?: string } | null)?.code === 'ER_DUP_ENTRY';
}
