import { createAuthToken } from '../../src/user/auth/jwt';
import { type Game } from '../../src/game/db/games';
import { pool } from '../../src/core/db/mysql';
import * as usersDb from '../../src/user/db/users';
import { type User } from '../../src/user/db/users';

// setup shared by the mocked tests. Each call makes new objects, so no test sees
// another test's changes

// the user organizing the test game
export function testOrganizer(): User {
  return {
    userId: 1,
    googleId: 'google-organizer',
    email: 'organizer@example.com',
    firstName: 'Olivia',
    lastName: 'Organizer',
    birthYear: 1995,
    // before the token is made, so requireAuth accepts it
    createdAt: new Date('2026-01-01T00:00:00Z'),
  };
}

// a user who joined the test game
export function testPlayer(): User {
  return {
    userId: 2,
    googleId: 'google-player',
    email: 'player@example.com',
    firstName: 'Paul',
    lastName: 'Player',
    birthYear: 1998,
    // before the token is made, so requireAuth accepts it
    createdAt: new Date('2026-01-01T00:00:00Z'),
  };
}

// an upcoming game organized by testOrganizer, with overrides for what the test is about
export function testGame(overrides: Partial<Game> = {}): Game {
  return {
    gameId: 42,
    organizerId: 1,
    title: 'Sunday Soccer',
    sport: 'soccer',
    description: null,
    startsAt: '2030-01-06T18:00:00Z',
    endsAt: null,
    locationName: 'Jericho Park',
    address: '3941 Point Grey Rd, Vancouver',
    latitude: 49.2722,
    longitude: -123.1957,
    maxPlayers: 10,
    playerCount: 3,
    skillLevel: 'any',
    status: 'scheduled',
    hasStarted: false,
    createdAt: '2026-10-01T00:00:00Z',
    ...overrides,
  };
}

// signs the user in: requireAuth looks them up (mocked), returns their token
export function signIn(user: User): string {
  jest.spyOn(usersDb, 'findUserById').mockResolvedValue(user);
  return createAuthToken(user.userId);
}

// undoes each test's mocks after it, and closes the database pool after the file's tests
export function cleanUpAfterTests(): void {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  afterAll(async () => {
    await pool.end();
  });
}
