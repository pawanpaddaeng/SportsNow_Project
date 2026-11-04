import { selectRows } from '../../../../src/core/db/mysql';
import { addGame, addUser, asUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let requesterId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: skill_level, play_frequency and message for another user's game
  // Expected status code: 201
  // Expected behavior: a pending request is saved with the answers
  // Expected output: { msg: "success", request } with the request's id, game_id and status "pending"
  test('Valid request', async () => {
    // 1. another user's upcoming game
    const gameId = await addGame(organizerId);

    // 2. ask to join through the interface
    const response = await asUser(requesterId).post(`/api/games/${gameId}/join-requests`)
      .send({ skill_level: 'intermediate', play_frequency: 'weekly', message: 'Happy to play any position' });

    // 3. check the reply
    expect(response.status).toBe(201);
    expect(response.body.msg).toBe('success');
    expect(response.body.request).toMatchObject({ game_id: gameId, status: 'pending' });

    // 4. check what was really saved
    const rows = await selectRows<Record<string, unknown>>(
      `SELECT request_id, skill_level, play_frequency, message, status
       FROM join_requests WHERE game_id = ? AND user_id = ?`,
      [gameId, requesterId],
    );
    expect(rows).toEqual([{
      request_id: response.body.request.request_id,
      skill_level: 'intermediate',
      play_frequency: 'weekly',
      message: 'Happy to play any position',
      status: 'pending',
    }]);
  });
});
