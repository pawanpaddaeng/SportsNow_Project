import { getGameById } from '../../../../src/game/db/games';
import { addGame, addUser, asUser, cleanUpAfterTests, ONE_DAY_MS, validGameBody } from '../../helpers';

let organizerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
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
    const response = await asUser(organizerId).put(`/api/games/${gameId}`)
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
});
