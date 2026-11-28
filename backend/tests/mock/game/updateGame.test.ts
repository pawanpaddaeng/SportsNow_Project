import * as push from '../../../src/core/notifications/push';
import * as gamesDb from '../../../src/game/db/games';
import type * as MockEnv from '../mockEnv';
import {
  api,
  cleanUpAfterTests,
  GAME_ID,
  letNotificationsFinish,
  ONE_DAY_MS,
  signIn,
  testGame,
  testOrganizer,
  validGameBody,
  withToken,
} from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Mocked: PUT /api/games/{gameId}', () => {
  // Mocked behavior: the games database is replaced: getGameById returns the game before the
  //   edit, then after it with a new title, sport, longitude, max players, skill level and
  //   description, and the same time, place name, address and latitude. updateGame reports a
  //   change and getUsersToNotify returns users 2 and 3. sendPushToUsers is replaced to check
  //   who is told and what. Sign in is mocked only so the route can run
  // Input: the organizer edits those details of their game
  // Expected status code: 200
  // Expected behavior: the players and pending requesters (not the organizer) get a
  //   game_updated notification naming every detail that changed, the moved pin counting as a
  //   new location
  // Expected output: { msg: "success", game } with the new title
  test('Players are told every detail that changed', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. a game whose title, sport, pin, spots, skill level and description change
    const before = testGame({ title: 'Old Title' });
    const after = testGame({
      title: 'New Title',
      sport: 'basketball',
      longitude: -123.2,
      maxPlayers: 12,
      skillLevel: 'beginner',
      description: 'Bring water',
    });
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValueOnce(before).mockResolvedValueOnce(after);
    jest.spyOn(gamesDb, 'updateGame').mockResolvedValue({ ok: true, changed: true });
    const getUsersToNotify = jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([2, 3]);
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(2);

    // 3. edit it through the interface, and let the notifications finish
    const response = await api.put(`/api/games/${GAME_ID}`).set(withToken(token))
      .send(validGameBody());
    await letNotificationsFinish();

    // 4. check the reply and that the notification names each change
    expect(response.status).toBe(200);
    expect(response.body.game.title).toBe('New Title');
    expect(getUsersToNotify).toHaveBeenCalledWith(GAME_ID, organizer.userId);
    expect(sendPushToUsers).toHaveBeenCalledTimes(1);
    expect(sendPushToUsers).toHaveBeenCalledWith([2, 3], {
      title: 'Game updated',
      body: 'New Title: the organizer changed the title, sport, location, max players, skill level, description',
      data: { type: 'game_updated', gameId: String(GAME_ID) },
    });
  });

  // Mocked behavior: the games database is replaced: getGameById returns the same game before
  //   and after, and updateGame reports no change. sendPushToUsers is replaced to check it
  //   isn't called. Sign in is mocked only so the route can run
  // Input: the organizer saves their game with the same values
  // Expected status code: 200
  // Expected behavior: no notification is sent
  // Expected output: { msg: "success", game }
  test('Nothing changed', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. a game the edit doesn't change
    const game = testGame();
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(game);
    jest.spyOn(gamesDb, 'updateGame').mockResolvedValue({ ok: true, changed: false });
    jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([2, 3]);
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(2);

    // 3. save the same values through the interface
    const response = await api.put(`/api/games/${GAME_ID}`).set(withToken(token))
      .send({
        title: game.title,
        sport: game.sport,
        starts_at: new Date(Date.now() + ONE_DAY_MS).toISOString(),
        location_name: game.locationName,
        address: game.address,
        latitude: game.latitude,
        longitude: game.longitude,
        max_players: game.maxPlayers,
        skill_level: game.skillLevel,
      });

    // 4. let any notifications finish

    await letNotificationsFinish();

    // 5. check the reply, and that nobody was told
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(sendPushToUsers).not.toHaveBeenCalled();
  });

  // Mocked behavior: getGameById returns the game and updateGame throws, as if the database
  //   failed while saving. Sign in is mocked only so the route can run
  // Input: the organizer edits their game
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not update game, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. make saving fail
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame());
    const updateGame = jest.spyOn(gamesDb, 'updateGame').mockRejectedValue(new Error('database down'));

    // 3. edit it through the interface
    const response = await api.put(`/api/games/${GAME_ID}`).set(withToken(token))
      .send(validGameBody());

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not update game, try again later' });
    expect(updateGame).toHaveBeenCalledTimes(1);
  });
});
