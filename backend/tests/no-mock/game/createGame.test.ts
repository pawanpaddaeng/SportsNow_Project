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

  // Input: a game with one bad field at a time, or no body at all
  // Expected status code: 400
  // Expected behavior: nothing is saved
  // Expected output: { msg: "invalid_request", error } naming the bad field
  test.each([
    ['no body', null, 'title is required (max 100 characters)'],
    ['a title that is not text', { title: 42 }, 'title is required (max 100 characters)'],
    ['a title over 100 characters', { title: 't'.repeat(101) }, 'title is required (max 100 characters)'],
    ['a sport that is not text', { sport: 5 }, 'sport must be one of: basketball, soccer, volleyball, tennis, other'],
    ['a description that is not text', { description: 7 }, 'description must be text (max 1000 characters)'],
    ['a description over 1000 characters', { description: 'd'.repeat(1001) }, 'description must be text (max 1000 characters)'],
    ['no starts_at', { starts_at: undefined }, 'starts_at is required as an ISO 8601 time, e.g. 2026-10-18T02:00:00Z'],
    ['a starts_at that is not a time', { starts_at: 'soon' }, 'starts_at is required as an ISO 8601 time, e.g. 2026-10-18T02:00:00Z'],
    ['a starts_at in the past', { starts_at: new Date(Date.now() - ONE_DAY_MS).toISOString() }, 'starts_at must be in the future'],
    ['a starts_at over a year away', { starts_at: new Date(Date.now() + 400 * ONE_DAY_MS).toISOString() }, 'starts_at must be within 365 days'],
    ['an ends_at that is not a time', { ends_at: 'later' }, 'ends_at must be an ISO 8601 time'],
    ['an ends_at before the start', { ends_at: new Date(Date.now()).toISOString() }, 'ends_at must be after starts_at'],
    ['no location_name', { location_name: undefined }, 'location_name is required (max 255 characters)'],
    ['a blank address', { address: '   ' }, 'address is required (max 255 characters)'],
    ['a latitude over 90', { latitude: 100 }, 'latitude (-90 to 90) and longitude (-180 to 180) are required numbers'],
    ['a longitude that is not a number', { longitude: 'west' }, 'latitude (-90 to 90) and longitude (-180 to 180) are required numbers'],
    ['max_players as text', { max_players: '10' }, 'max_players must be a whole number from 2 to 100'],
    ['max_players that is not whole', { max_players: 2.5 }, 'max_players must be a whole number from 2 to 100'],
    ['max_players below 2', { max_players: 1 }, 'max_players must be a whole number from 2 to 100'],
    ['max_players over 100', { max_players: 101 }, 'max_players must be a whole number from 2 to 100'],
    ['an unknown skill_level', { skill_level: 'pro' }, 'skill_level must be one of: any, beginner, intermediate, advanced'],
    ['a skill_level that is not text', { skill_level: 3 }, 'skill_level must be one of: any, beginner, intermediate, advanced'],
  ])('Rejects a game with %s', async (_case, change, error) => {
    const request = api.post('/api/games').set(withToken(token));
    const response = await (change === null ? request : request.send(validGameBody(change)));

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error });
    const games = await selectRows<{ game_id: number }>('SELECT game_id FROM games WHERE organizer_id = ?', [organizerId]);
    expect(games).toEqual([]);
  });

  // Input: a valid game without skill_level
  // Expected status code: 201
  // Expected behavior: the game is saved for any skill level, the default
  // Expected output: { msg: "success", game } with skill_level "any"
  test('A game without skill_level is for any level', async () => {
    const body = validGameBody();
    delete body.skill_level;

    const response = await api.post('/api/games').set(withToken(token)).send(body);

    expect(response.status).toBe(201);
    expect(response.body.game).toMatchObject({ skill_level: 'any' });
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
