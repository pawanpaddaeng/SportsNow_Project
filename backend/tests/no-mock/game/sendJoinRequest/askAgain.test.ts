import { executeWrite, selectRows } from '../../../../src/core/db/mysql';
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
  // Input: new answers from a user whose earlier request for the game was declined
  // Expected status code: 201
  // Expected behavior: the same request goes back to pending with the new answers, no second one is added
  // Expected output: { msg: "success", request } with the same request id and status "pending"
  test('Ask again', async () => {
    // 1. a game the user's earlier request was declined for
    const gameId = await addGame(organizerId);
    const declined = await executeWrite(
      `INSERT INTO join_requests (game_id, user_id, skill_level, play_frequency, message, status)
       VALUES (?, ?, 'beginner', 'rarely', 'First try', 'declined')`,
      [gameId, requesterId],
    );

    // 2. ask again with new answers through the interface
    const response = await asUser(requesterId).post(`/api/games/${gameId}/join-requests`)
      .send({ skill_level: 'advanced', play_frequency: 'weekly', message: 'Second try' });

    // 3. check the reply
    expect(response.status).toBe(201);
    expect(response.body.request).toMatchObject({ request_id: declined.insertId, status: 'pending' });

    // 4. check there's still one request, pending with the new answers
    const rows = await selectRows<Record<string, unknown>>(
      `SELECT request_id, skill_level, play_frequency, message, status
       FROM join_requests WHERE game_id = ? AND user_id = ?`,
      [gameId, requesterId],
    );
    expect(rows).toEqual([{
      request_id: declined.insertId,
      skill_level: 'advanced',
      play_frequency: 'weekly',
      message: 'Second try',
      status: 'pending',
    }]);
  });
});
