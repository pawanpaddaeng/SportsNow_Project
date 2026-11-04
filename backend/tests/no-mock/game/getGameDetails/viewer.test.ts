import { addGame, addJoinRequest, addUser, api, asUser, cleanUpAfterTests } from '../../helpers';

let organizerId: number;
let viewerId: number;

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  viewerId = await addUser('Vera');
});

cleanUpAfterTests();

// Interface GET /api/games/{gameId}
describe('Unmocked: GET /api/games/{gameId}', () => {
  // Input: a user opens a game they asked to join
  // Expected status code: 200
  // Expected behavior: the user sees they are only viewing it and their request, nothing changes
  // Expected output: { msg: "success", game, organizer, players, ... } with viewer_role
  //   "viewer" and join_request_status "pending"
  test('Viewer', async () => {
    // 1. a game the user asked to join
    const gameId = await addGame(organizerId);
    await addJoinRequest(gameId, viewerId);

    // 2. open the game through the interface
    const response = await api.get(`/api/games/${gameId}`).set(asUser(viewerId));

    // 3. check the viewer's view
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game.game_id).toBe(gameId);
    expect(response.body.viewer_role).toBe('viewer');
    expect(response.body.join_request_status).toBe('pending');
  });
});
