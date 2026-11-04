import { addGame, addPlayer, addUser, asUser, cleanUpAfterTests } from '../../helpers';

let userId: number;
let otherUserId: number;

beforeEach(async () => {
  userId = await addUser('Uma');
  otherUserId = await addUser('Omar');
});

cleanUpAfterTests();

// Interface GET /api/games/mine
describe('Unmocked: GET /api/games/mine', () => {
  // Input: a signed in user who organizes one game and plays in another user's game
  // Expected status code: 200
  // Expected behavior: each game is listed under the user's role in it, nothing changes
  // Expected output: { msg: "success", organized_games, joined_games } with one game in each
  test('Organized and joined', async () => {
    // 1. one game the user organizes, and one they joined
    const organizedGameId = await addGame(userId, { title: 'My Game' });
    const joinedGameId = await addGame(otherUserId, { title: 'Their Game' });
    await addPlayer(joinedGameId, userId);

    // 2. get the user's games through the interface
    const response = await asUser(userId).get('/api/games/mine');

    // 3. check each game is in the right list
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.organized_games).toHaveLength(1);
    expect(response.body.organized_games[0]).toMatchObject({
      game_id: organizedGameId,
      organizer_id: userId,
      title: 'My Game',
      player_count: 1,
      pending_requests: 0,
    });
    expect(response.body.joined_games).toHaveLength(1);
    expect(response.body.joined_games[0]).toMatchObject({
      game_id: joinedGameId,
      organizer_id: otherUserId,
      title: 'Their Game',
      player_count: 2,
    });
  });
});
