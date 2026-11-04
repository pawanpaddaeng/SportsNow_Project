import { selectRows } from '../../../../src/core/db/mysql';
import { addGame, addUser, api, asUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: { skill_level: "beginner" } for another user's game
  // Expected status code: 201
  // Expected behavior: the request is saved, with play_frequency and message empty
  // Expected output: { msg: "success", request } with status "pending"
  test('Only skill level', async () => {
    // 1. another user's upcoming game
    const gameId = await addGame(organizerId);

    // 2. ask to join with only the required answer through the interface
    const response = await api.post(`/api/games/${gameId}/join-requests`).set(asUser(requesterId))
      .send({ skill_level: 'beginner' });

    // 3. check the reply
    expect(response.status).toBe(201);
    expect(response.body.request).toMatchObject({ game_id: gameId, status: 'pending' });

    // 4. check what was really saved
    const rows = await selectRows<Record<string, unknown>>(
      'SELECT skill_level, play_frequency, message, status FROM join_requests WHERE game_id = ? AND user_id = ?',
      [gameId, requesterId],
    );
    expect(rows).toEqual([{ skill_level: 'beginner', play_frequency: null, message: null, status: 'pending' }]);
  });
});
