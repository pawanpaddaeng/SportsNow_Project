import { executeWrite, pool, selectRows } from '../../src/core/db/mysql';
import { logError, logInfo } from '../../src/core/logger';
import { createChatMessage, getGameChatId } from '../../src/chat/db/chats';
import { decideFriendRequest, sendFriendRequest } from '../../src/friend/db/friends';
import { createGame, type NewGame } from '../../src/game/db/games';
import { createJoinRequest, type NewJoinRequest } from '../../src/game/db/joinRequests';
import { createAuthToken } from '../../src/user/auth/jwt';
import { createUser } from '../../src/user/db/users';

/*  Saves the games the frontend E2E tests for game discovery need (DiscoverGameTest,
    JoinGameTest, MyGamesTest and GameChatTest), and the games, friends and players the game
    planning tests need (GamePlanningTest), see Testing.md section 4. Run by
    scripts/run-frontend-e2e-tests inside the backend container, once User A has signed in:

      docker compose exec -T backend npx tsx tests/e2e/seedGameDiscovery.ts <User A's email>

    Every run first removes what an earlier run saved, so the tests always start from the same
    games. The games belong to a test organizer, except one organized by User A, since Discover
    never shows a user their own games. User B (the manual tester's "Bea Tester" account) is
    added to the chat game when that account exists. Prints E2E_ORGANIZER_TOKEN=<token>: the tests sign in
    as the test organizer with it to cancel or start a game while User A is joining it.
    For the game planning tests it saves User A a game per test, two friends to invite, a
    pending join request in two of the games and a player in another.
 */

// the test organizer's Google id, which no real account has
const ORGANIZER_GOOGLE_ID = 'e2e-test-organizer';

// every seeded game's title starts with this, which is how User A's seeded game is found again
const TITLE_PREFIX = 'E2E ';

// metres in one degree of latitude on the sphere ST_Distance_Sphere measures on (radius 6,370,986 m),
// so Discover shows the games exactly 200 m, 1 km and 2 km away
const METRES_PER_DEGREE = (6_370_986 * Math.PI) / 180;

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

/*  the tests' two fake phone locations, in open ocean so no real game is within Discover's
    50 km. The Discover games are near DISCOVER_SPOT. The other games are near GAMES_SPOT, about
    111 km away, so they don't change what Discover shows at DISCOVER_SPOT
 */
const DISCOVER_SPOT = { latitude: 45, longitude: -135 };
const GAMES_SPOT = { latitude: 46, longitude: -135 };

// the games, with how far north of their spot each one is. Titles must match the E2E tests
interface SeedGame {
  title: string;
  sport: string;
  spot: { latitude: number; longitude: number };
  metresNorth: number;
}

const DISCOVER_GAMES: SeedGame[] = [
  { title: 'E2E Basketball Near', sport: 'basketball', spot: DISCOVER_SPOT, metresNorth: 200 },
  { title: 'E2E Basketball Far', sport: 'basketball', spot: DISCOVER_SPOT, metresNorth: 1000 },
  { title: 'E2E Soccer', sport: 'soccer', spot: DISCOVER_SPOT, metresNorth: 2000 },
];

// one game per Join Game test, so each test starts with a game User A never asked to join
const JOIN_GAMES: SeedGame[] = [
  { title: 'E2E Join', sport: 'basketball', spot: GAMES_SPOT, metresNorth: 100 },
  { title: 'E2E Join Cancelled', sport: 'basketball', spot: GAMES_SPOT, metresNorth: 200 },
  { title: 'E2E Join Started', sport: 'basketball', spot: GAMES_SPOT, metresNorth: 300 },
  { title: 'E2E Join Offline', sport: 'basketball', spot: GAMES_SPOT, metresNorth: 400 },
];

// games User A has already joined: one to leave, one to chat in
const LEAVE_GAME: SeedGame = { title: 'E2E Leave', sport: 'soccer', spot: GAMES_SPOT, metresNorth: 500 };
const CHAT_GAME: SeedGame = { title: 'E2E Chat', sport: 'volleyball', spot: GAMES_SPOT, metresNorth: 600 };

// the game User A organizes
const ORGANIZED_GAME: SeedGame = { title: 'E2E Organized', sport: 'tennis', spot: GAMES_SPOT, metresNorth: 700 };

// the earlier messages in the chat game, oldest first. The tests check they're shown
const CHAT_MESSAGES = ['E2E hello from the organizer', 'E2E bring a ball please'];

// User B, the manual tester's account (see Testing.md), who sends a live message in the chat game
const USER_B = { firstName: 'Bea', lastName: 'Tester' };

// a test user for the game planning tests, found again by their Google id, which no real account has
interface SeedUser {
  googleId: string;
  firstName: string;
  lastName: string;
}

// User A's friends, who GamePlanningTest invites to a game
const FRIENDS: SeedUser[] = [
  { googleId: 'e2e-test-fiona', firstName: 'Fiona', lastName: 'Friend' },
  { googleId: 'e2e-test-finn', firstName: 'Finn', lastName: 'Friend' },
];

// players asking to join User A's games, one is accepted and one rejected, and a player User A removes
const ACCEPTED_ASKER: SeedUser = { googleId: 'e2e-test-alex', firstName: 'Alex', lastName: 'Asker' };
const REJECTED_ASKER: SeedUser = { googleId: 'e2e-test-blake', firstName: 'Blake', lastName: 'Asker' };
const REMOVED_PLAYER: SeedUser = { googleId: 'e2e-test-riley', firstName: 'Riley', lastName: 'Player' };

const PLANNING_USERS = [...FRIENDS, ACCEPTED_ASKER, REJECTED_ASKER, REMOVED_PLAYER];

// User A's games for the game planning tests, one per test. Titles must match GamePlanningTest
const EDIT_GAME: SeedGame = { title: 'E2E Edit', sport: 'basketball', spot: GAMES_SPOT, metresNorth: 800 };
const CANCEL_GAME: SeedGame = { title: 'E2E Cancel', sport: 'soccer', spot: GAMES_SPOT, metresNorth: 900 };
const INVITE_GAME: SeedGame = { title: 'E2E Invite', sport: 'volleyball', spot: GAMES_SPOT, metresNorth: 1000 };
const ACCEPT_GAME: SeedGame = { title: 'E2E Accept', sport: 'basketball', spot: GAMES_SPOT, metresNorth: 1100 };
const REJECT_GAME: SeedGame = { title: 'E2E Reject', sport: 'basketball', spot: GAMES_SPOT, metresNorth: 1200 };
const REMOVE_GAME: SeedGame = { title: 'E2E Remove', sport: 'tennis', spot: GAMES_SPOT, metresNorth: 1300 };

// the join requests' answers, which the tests check are shown to User A
const ACCEPTED_REQUEST: NewJoinRequest = {
  skillLevel: 'intermediate', playFrequency: 'weekly', message: 'Happy to play any position!',
};
const REJECTED_REQUEST: NewJoinRequest = {
  skillLevel: 'beginner', playFrequency: 'rarely', message: 'First time playing, hope that is okay.',
};

// the user who signed in to the app on the emulator. email isn't unique, so a match must be one user
async function findUserA(email: string): Promise<number> {
  const rows = await selectRows<{ user_id: number }>('SELECT user_id FROM users WHERE email = ?', [email]);
  const user = rows[0];
  if (!user) throw new Error(`No user has the email ${email}. Sign in to the app on the emulator first.`);
  if (rows.length > 1) throw new Error(`More than one user has the email ${email}.`);
  return user.user_id;
}

/*  removes what an earlier run saved. Deleting the test organizer also deletes their games,
    the games' players, chats, join requests and notifications (ON DELETE CASCADE), and
    deleting the game planning users also deletes their friendships with User A. User A's
    games include the ones CreateGameTest made, whose titles also start with "E2E "
 */
async function removeEarlierRun(userAId: number): Promise<void> {
  await executeWrite('DELETE FROM users WHERE google_id = ?', [ORGANIZER_GOOGLE_ID]);
  await executeWrite('DELETE FROM games WHERE organizer_id = ? AND title LIKE ?', [userAId, `${TITLE_PREFIX}%`]);
  for (const user of PLANNING_USERS) {
    await executeWrite('DELETE FROM users WHERE google_id = ?', [user.googleId]);
  }
}

/*  saves the game, the nth one starting n days from now so the lists the tests read are always
    in the same order. Returns its id
 */
function addGame(organizerId: number, game: SeedGame, n: number): Promise<number> {
  const newGame: NewGame = {
    organizerId,
    title: game.title,
    sport: game.sport,
    description: 'Saved by the E2E test seed script',
    startsAt: new Date(Date.now() + (n + 1) * ONE_DAY_MS),
    endsAt: null,
    locationName: `${game.title} Court`,
    address: 'E2E test address',
    latitude: game.spot.latitude + game.metresNorth / METRES_PER_DEGREE,
    longitude: game.spot.longitude,
    maxPlayers: 10,
    skillLevel: 'any',
  };
  return createGame(newGame);
}

// User B's user_id, null if the manual tester hasn't signed up as User B yet
async function findUserB(): Promise<number | null> {
  const rows = await selectRows<{ user_id: number }>(
    'SELECT user_id FROM users WHERE first_name = ? AND last_name = ?', [USER_B.firstName, USER_B.lastName]);
  if (rows.length > 1) throw new Error(`More than one user is named ${USER_B.firstName} ${USER_B.lastName}.`);
  return rows[0]?.user_id ?? null;
}

// puts a user in the game, as if the organizer had approved their join request
async function addPlayer(gameId: number, userId: number): Promise<void> {
  await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, userId]);
}

// saves a game planning test user. Returns their id
function addUser(user: SeedUser): Promise<number> {
  return createUser({
    googleId: user.googleId,
    email: `${user.googleId}@example.com`,
    firstName: user.firstName,
    lastName: user.lastName,
    birthYear: 1995,
  });
}

// makes the two users friends, as the app does when one accepts the other's friend request
async function addFriendship(userAId: number, friendId: number): Promise<void> {
  const request = await sendFriendRequest(friendId, userAId);
  if (!request.ok) throw new Error(`Could not send the friend request: ${request.problem}`);
  const decision = await decideFriendRequest(request.requestId, userAId, true);
  if (!decision.ok) throw new Error(`Could not accept the friend request: ${decision.problem}`);
}

// the user asks to join the game, as the app's join request form does
async function addJoinRequest(gameId: number, userId: number, answers: NewJoinRequest): Promise<void> {
  const result = await createJoinRequest(gameId, userId, answers);
  if (!result.ok) throw new Error(`Could not save the join request: ${result.problem}`);
}

/*  saves what GamePlanningTest needs, starting with day n: User A's game for each test, their
    two friends, a pending join request in the accept and reject games, and a player in the
    remove game. Every game has only User A in it otherwise
 */
async function seedGamePlanning(userAId: number, n: number): Promise<void> {
  for (const friend of FRIENDS) {
    await addFriendship(userAId, await addUser(friend));
  }

  await addGame(userAId, EDIT_GAME, n);
  await addGame(userAId, CANCEL_GAME, n + 1);
  await addGame(userAId, INVITE_GAME, n + 2);
  const acceptGameId = await addGame(userAId, ACCEPT_GAME, n + 3);
  const rejectGameId = await addGame(userAId, REJECT_GAME, n + 4);
  const removeGameId = await addGame(userAId, REMOVE_GAME, n + 5);

  await addJoinRequest(acceptGameId, await addUser(ACCEPTED_ASKER), ACCEPTED_REQUEST);
  await addJoinRequest(rejectGameId, await addUser(REJECTED_ASKER), REJECTED_REQUEST);
  await addPlayer(removeGameId, await addUser(REMOVED_PLAYER));
}

async function seed(email: string): Promise<void> {
  const userAId = await findUserA(email);
  await removeEarlierRun(userAId);

  const organizerId = await createUser({
    googleId: ORGANIZER_GOOGLE_ID,
    email: 'e2e-organizer@example.com',
    firstName: 'Olive',
    lastName: 'Organizer',
    birthYear: 1990,
  });

  const organizerGames = [...DISCOVER_GAMES, ...JOIN_GAMES];
  for (const [n, game] of organizerGames.entries()) {
    await addGame(organizerId, game, n);
  }

  const leaveGameId = await addGame(organizerId, LEAVE_GAME, organizerGames.length);
  await addPlayer(leaveGameId, userAId);

  const chatGameId = await addGame(organizerId, CHAT_GAME, organizerGames.length + 1);
  await addPlayer(chatGameId, userAId);
  const chatId = await getGameChatId(chatGameId);
  if (chatId === null) throw new Error('The chat game has no chat');
  for (const text of CHAT_MESSAGES) {
    await createChatMessage(chatId, organizerId, text);
  }
  const userBId = await findUserB();
  if (userBId === null) {
    logInfo(`No user is named ${USER_B.firstName} ${USER_B.lastName} yet, so GameChatTest's Receive test will fail`);
  } else {
    await addPlayer(chatGameId, userBId);
  }

  await addGame(userAId, ORGANIZED_GAME, organizerGames.length + 2);
  await seedGamePlanning(userAId, organizerGames.length + 3);

  logInfo(`Seeded the game discovery and game planning E2E games for user ${userAId}`);
  logInfo(`E2E_ORGANIZER_TOKEN=${createAuthToken(organizerId)}`);
}

async function main(): Promise<void> {
  const email = process.argv[2];
  if (!email) {
    throw new Error('Usage: npx tsx tests/e2e/seedGameDiscovery.ts <User A\'s email>');
  }
  try {
    await seed(email);
  } finally {
    await pool.end();
  }
}

main().catch((error: unknown) => {
  logError('Could not seed the E2E games', error);
  process.exitCode = 1;
});
