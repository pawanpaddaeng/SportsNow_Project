import request from 'supertest';

import { createApp } from '../../../src/app';
import * as mysql from '../../../src/core/db/mysql';
import { pool, type SqlValue } from '../../../src/core/db/mysql';
import { cleanUpAfterTests, signIn, testOrganizer } from '../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../src/core/config/env', () => jest.requireActual('../mockEnv'));

const GAME_ID = 42;

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/leave
describe('Mocked: POST /api/games/{gameId}/leave (last player)', () => {
  // Mocked behavior: the database itself is replaced by an in-memory games row and
  //   game_players list, so the real leaveGame runs its real queries against them.
  //   pool.getConnection gives a fake connection for the transaction. Sign in is mocked
  //   only so the route can run
  // Input: the organizer, the only player left in their cancelled game 42, leaves it
  //   (the organizer is always a player, and can only leave once the game is cancelled)
  // Expected status code: 200
  // Expected behavior: the organizer is no longer in the game, and with no players
  //   left the game is deleted
  // Expected output: { msg: "success" }
  test('Last player leaves and the game is deleted', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. make a mock game: a cancelled game whose only player is the organizer, in an
    //    in-memory stand-in for the games and game_players tables
    const db = {
      game: {
        game_id: GAME_ID,
        organizer_id: organizer.userId,
        title: 'Sunday Soccer',
        sport: 'soccer',
        description: null,
        starts_at: '2030-01-06T18:00:00Z',
        ends_at: null,
        location_name: 'Jericho Park',
        address: '3941 Point Grey Rd, Vancouver',
        latitude: '49.272200',
        longitude: '-123.195700',
        max_players: 10,
        skill_level: 'any',
        status: 'cancelled',
        has_started: 0,
        created_at_unix: 1790000000,
      } as Record<string, unknown> | null,
      players: [organizer.userId],
    };
    const connection = {
      beginTransaction: jest.fn().mockResolvedValue(undefined),
      commit: jest.fn().mockResolvedValue(undefined),
      rollback: jest.fn().mockResolvedValue(undefined),
      release: jest.fn(),
    };
    jest.spyOn(pool, 'getConnection').mockResolvedValue(connection as never);
    // SELECTs: the locked game row for leaveGame, or the game row for reading it back
    jest.spyOn(mysql, 'selectRows').mockImplementation(((sql: string, params: SqlValue[]) => {
      if (!db.game) return Promise.resolve([]);
      const playerCount = db.players.length;
      if (sql.includes('FOR UPDATE')) {
        const isPlayer = db.players.includes(Number(params[0])) ? 1 : 0;
        return Promise.resolve([{ ...db.game, player_count: playerCount, is_player: isPlayer }]);
      }
      return Promise.resolve([{ ...db.game, player_count: playerCount }]);
    }) as unknown as typeof mysql.selectRows);
    // writes: removing a player from game_players, and deleting the game itself
    jest.spyOn(mysql, 'executeWrite').mockImplementation((sql: string, params: SqlValue[]) => {
      if (sql.includes('DELETE FROM game_players')) {
        db.players = db.players.filter((userId) => userId !== Number(params[1]));
      } else if (/DELETE FROM games\b/.test(sql)) {
        db.game = null;
      }
      return Promise.resolve({ insertId: 0, affectedRows: 1, changedRows: 1 });
    });
    expect(db.players).toEqual([organizer.userId]);

    // 3. leave it through the interface
    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/leave`)
      .set('Authorization', `Bearer ${token}`);

    // 4. check the organizer left and the empty game was deleted
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(db.players).toEqual([]);
    expect(db.game).toBeNull();
  });
});
