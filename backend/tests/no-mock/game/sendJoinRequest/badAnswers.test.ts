import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool, selectRows } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const TOO_LONG_MESSAGE_LENGTH = 201;

let organizerId: number;
let requesterId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `send-join-request-test-badAnswers-${firstName}`,
    email: 'send-join-request-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  requesterId = await addUser('Rae');
});

// deleting the users also deletes their games, game players, chats and join requests
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(requesterId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: no skill_level, an unknown play_frequency, or a message of 201 characters
  // Expected status code: 400
  // Expected behavior: nothing is saved
  // Expected output: { msg: "invalid_request", error } naming the bad field
  test.each([
    ['no skill_level', {}, 'skill_level must be one of: beginner, intermediate, advanced, competitive'],
    [
      'an unknown play_frequency',
      { skill_level: 'beginner', play_frequency: 'daily' },
      'play_frequency must be one of: rarely, monthly, weekly, several_per_week',
    ],
    [
      'a message of 201 characters',
      { skill_level: 'beginner', message: 'x'.repeat(TOO_LONG_MESSAGE_LENGTH) },
      'message must be text (max 200 characters)',
    ],
  ])('Bad answers: %s', async (_name, body, error) => {
    const gameId = await createGame({
      organizerId,
      title: 'Evening Basketball',
      sport: 'basketball',
      description: null,
      startsAt: new Date(Date.now() + ONE_DAY_MS),
      endsAt: null,
      locationName: 'UBC Rec Centre',
      address: '6000 Student Union Blvd',
      latitude: 49.26,
      longitude: -123.25,
      maxPlayers: 10,
      skillLevel: 'any',
    });

    const response = await request(createApp())
      .post(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(requesterId)}`)
      .send(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error });
    const rows = await selectRows<{ request_id: number }>(
      'SELECT request_id FROM join_requests WHERE game_id = ?', [gameId]);
    expect(rows).toEqual([]);
  });
});
