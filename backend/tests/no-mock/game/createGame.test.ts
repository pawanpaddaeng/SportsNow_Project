import { selectRows } from '../../../src/core/db/mysql';
import { createAuthToken } from '../../../src/user/auth/jwt';
import { addUser, api, cleanUpAfterTests, getPlayerIds, ONE_DAY_MS, validGameBody, withToken } from '../helpers';

const TWO_HOURS_MS = 2 * 60 * 60 * 1000;

let organizerId: number;
let token: string;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  token = createAuthToken(organizerId);
});

cleanUpAfterTests();

// Interface POST /api/games
describe('Unmocked: POST /api/games', () => {
  // Input: a valid game with every field, starting tomorrow, from a signed in user
  // Expected status code: 201
  // Expected behavior: the game is saved with the user as its organizer and first player,
  //   and its chat is opened
  // Expected output: { msg: "success", game } with the stored game, player_count 1 and
  //   status "scheduled"
  test('Organizer creates a game', async () => {
    // 1. a game starting tomorrow, on a whole second since the database stores seconds
    const startsAt = new Date(Math.floor((Date.now() + ONE_DAY_MS) / 1000) * 1000);
    const endsAt = new Date(startsAt.getTime() + TWO_HOURS_MS);

    // 2. create it through the interface
    const response = await api.post('/api/games').set(withToken(token))
      .send({
        title: 'Evening Basketball',
        sport: 'basketball',
        description: 'Friendly game',
        starts_at: startsAt.toISOString(),
        ends_at: endsAt.toISOString(),
        location_name: 'UBC Rec Centre',
        address: '6000 Student Union Blvd',
        latitude: 49.26,
        longitude: -123.25,
        max_players: 10,
        skill_level: 'any',
      });

    // 3. check the reply
    expect(response.status).toBe(201);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({
      organizer_id: organizerId,
      title: 'Evening Basketball',
      sport: 'basketball',
      description: 'Friendly game',
      location_name: 'UBC Rec Centre',
      address: '6000 Student Union Blvd',
      latitude: 49.26,
      longitude: -123.25,
      max_players: 10,
      player_count: 1,
      skill_level: 'any',
      status: 'scheduled',
      has_started: false,
    });
    expect(new Date(response.body.game.starts_at as string).getTime()).toBe(startsAt.getTime());
    expect(new Date(response.body.game.ends_at as string).getTime()).toBe(endsAt.getTime());

    // 4. check what was really saved
    const gameId: number = response.body.game.game_id;
    const games = await selectRows<{ organizer_id: number }>(
      'SELECT organizer_id FROM games WHERE game_id = ?', [gameId]);
    expect(games).toEqual([{ organizer_id: organizerId }]);

    expect(await getPlayerIds(gameId)).toEqual([organizerId]);

    const chats = await selectRows<{ chat_id: number }>(
      'SELECT chat_id FROM chats WHERE game_id = ?', [gameId]);
    expect(chats).toHaveLength(1);
  });

  // Input: a valid game without its title, from a signed in user
  // Expected status code: 400
  // Expected behavior: the request is rejected before anything is saved
  // Expected output: { msg: "invalid_request", error: "title is required (max 100 characters)" }
  test('Invalid body never reaches the database', async () => {
    const response = await api.post('/api/games').set(withToken(token))
      .send(validGameBody({ title: undefined }));

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'title is required (max 100 characters)' });
    const games = await selectRows<{ game_id: number }>('SELECT game_id FROM games WHERE organizer_id = ?', [organizerId]);
    expect(games).toEqual([]);
  });

  // Input: a body sent as JSON that isn't valid JSON, "{bad", from a signed in user
  // Expected status code: 400
  // Expected behavior: the body can't be read, so the request is turned down before the route
  //   runs, by Express's own handling instead of a server error. No game is saved
  // Expected output: Express's 400 Bad Request reply
  test('A body that is not valid JSON is turned down', async () => {
    const response = await api.post('/api/games').set(withToken(token))
      .set('Content-Type', 'application/json').send('{bad');

    expect(response.status).toBe(400);
    const games = await selectRows<{ game_id: number }>('SELECT game_id FROM games WHERE organizer_id = ?', [organizerId]);
    expect(games).toEqual([]);
  });
});
