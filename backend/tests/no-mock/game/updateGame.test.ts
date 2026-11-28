import { getGameById } from '../../../src/game/db/games';
import {
  addCancelledGame,
  addGame,
  addPlayer,
  addStartedGame,
  addUser,
  api,
  asUser,
  cleanUpAfterTests,
  MISSING_GAME_ID,
  ONE_DAY_MS,
  validGameBody,
} from '../helpers';

let organizerId: number;
let otherUserId: number;
let playerIds: number[];

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  otherUserId = await addUser('Omar');
  playerIds = [await addUser('Paul'), await addUser('Pia')];
});

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: the organizer changes their game's title and start time
  // Expected status code: 200
  // Expected behavior: the game is updated in the database
  // Expected output: { msg: "success", game } with the new title and time
  test('Valid edit', async () => {
    // 1. a game starting tomorrow
    const gameId = await addGame(organizerId, { title: 'Original Title' });

    // 2. a new time for it, on a whole second since the database stores seconds
    const newStartsAt = new Date(Math.floor((Date.now() + 2 * ONE_DAY_MS) / 1000) * 1000);

    // 3. edit it through the interface
    const response = await api.put(`/api/games/${gameId}`).set(asUser(organizerId))
      .send(validGameBody({ starts_at: newStartsAt.toISOString() }));

    // 4. check the reply
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: gameId, title: 'New Title' });
    expect(new Date(response.body.game.starts_at as string).getTime()).toBe(newStartsAt.getTime());

    // 5. check what was really saved
    const saved = await getGameById(gameId);
    expect(saved?.title).toBe('New Title');
    expect(new Date(saved?.startsAt ?? 0).getTime()).toBe(newStartsAt.getTime());
  });

  // Input: the organizer edits their game with sport "chess"
  // Expected status code: 400
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "invalid_request", error } listing the allowed sports
  test('Bad field', async () => {
    // 1. a game to edit
    const gameId = await addGame(organizerId, { title: 'Original Title' });

    // 2. edit it with a sport that isn't allowed through the interface
    const response = await api.put(`/api/games/${gameId}`).set(asUser(organizerId))
      .send(validGameBody({ sport: 'chess' }));

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'sport must be one of: basketball, soccer, volleyball, tennis, other',
    });
    const saved = await getGameById(gameId);
    expect(saved?.title).toBe('Original Title');
    expect(saved?.sport).toBe('basketball');
  });

  // Input: the organizer lowers max_players to 2 when the game has 3 players
  // Expected status code: 400
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "invalid_request", error: "max_players can't be below the 3 players already in the game" }
  test('Too few spots', async () => {
    // 1. a game with 3 players: the organizer and two who joined
    const gameId = await addGame(organizerId, { title: 'Original Title' });
    await Promise.all(playerIds.map((playerId) => addPlayer(gameId, playerId)));

    // 2. lower max_players below that through the interface
    const response = await api.put(`/api/games/${gameId}`).set(asUser(organizerId))
      .send(validGameBody({ max_players: 2 }));

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: "max_players can't be below the 3 players already in the game",
    });
    const saved = await getGameById(gameId);
    expect(saved?.title).toBe('Original Title');
    expect(saved?.maxPlayers).toBe(10);
  });

  // Input: the organizer edits their cancelled game
  // Expected status code: 409
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "game_cancelled", error: "This game was cancelled" }
  test('Cancelled game', async () => {
    // 1. a cancelled game
    const gameId = await addCancelledGame(organizerId, { title: 'Original Title' });

    // 2. edit it through the interface
    const response = await api.put(`/api/games/${gameId}`).set(asUser(organizerId))
      .send(validGameBody());

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'game_cancelled', error: 'This game was cancelled' });
    expect((await getGameById(gameId))?.title).toBe('Original Title');
  });

  // Input: the organizer edits their game that started yesterday
  // Expected status code: 409
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "game_started", error } with the reason
  test('Game already started', async () => {
    // 1. a game that started yesterday
    const gameId = await addStartedGame(organizerId, { title: 'Original Title' });

    // 2. edit it through the interface
    const response = await api.put(`/api/games/${gameId}`).set(asUser(organizerId))
      .send(validGameBody());

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(409);
    expect(response.body.msg).toBe('game_started');
    expect((await getGameById(gameId))?.title).toBe('Original Title');
  });

  // Input: a user who isn't the organizer edits the game
  // Expected status code: 403
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "forbidden", error: "Only the organizer can change this game" }
  test('Not the organizer', async () => {
    const gameId = await addGame(organizerId, { title: 'Original Title' });

    const response = await api.put(`/api/games/${gameId}`).set(asUser(otherUserId))
      .send(validGameBody());

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can change this game' });
    expect((await getGameById(gameId))?.title).toBe('Original Title');
  });

  // Input: a signed in user edits an id with no game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "not_found", error: "This game does not exist" }
  test("Game doesn't exist", async () => {
    const response = await api.put(`/api/games/${MISSING_GAME_ID}`).set(asUser(otherUserId))
      .send(validGameBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'not_found', error: 'This game does not exist' });
  });

  // Input: a game from tomorrow at 6 PM to 8 PM is edited without ends_at, so its end is kept,
  //   with a new start the day after
  // Expected status code: 400
  // Expected behavior: the game would end before it starts, so it doesn't change
  // Expected output: { msg: "invalid_request", error: "starts_at must be before the game ends" }
  test('Moving the start past the kept end time', async () => {
    const startsAt = new Date(Date.now() + ONE_DAY_MS);
    const gameId = await addGame(organizerId, { startsAt, endsAt: new Date(startsAt.getTime() + 2 * 60 * 60 * 1000) });

    const response = await api.put(`/api/games/${gameId}`).set(asUser(organizerId))
      .send(validGameBody({ starts_at: new Date(Date.now() + 2 * ONE_DAY_MS).toISOString() }));

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'starts_at must be before the game ends' });
    expect((await getGameById(gameId))?.title).toBe('Evening Basketball');
  });

  // Input: the organizer edits their game, which has no end time, sending an ends_at 2 hours
  //   after its start
  // Expected status code: 200
  // Expected behavior: the new end time is saved
  // Expected output: { msg: "success", game } with that ends_at
  test('An edit can set the end time', async () => {
    const gameId = await addGame(organizerId);
    const startsAt = new Date(Math.floor((Date.now() + 2 * ONE_DAY_MS) / 1000) * 1000);
    const endsAt = new Date(startsAt.getTime() + 2 * 60 * 60 * 1000);

    const response = await api.put(`/api/games/${gameId}`).set(asUser(organizerId))
      .send(validGameBody({ starts_at: startsAt.toISOString(), ends_at: endsAt.toISOString() }));

    expect(response.status).toBe(200);
    expect(new Date(response.body.game.ends_at as string).getTime()).toBe(endsAt.getTime());
    expect(new Date((await getGameById(gameId))?.endsAt ?? 0).getTime()).toBe(endsAt.getTime());
  });
});
