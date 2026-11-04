import request from 'supertest';

import { createApp } from '../src/app';

// setup shared by the mocked and unmocked tests

export const ONE_DAY_MS = 24 * 60 * 60 * 1000;

// the calls a signed in test can make, each already carrying the sign in token
export interface SignedInApi {
  get: (path: string) => request.Test;
  post: (path: string) => request.Test;
  put: (path: string) => request.Test;
  patch: (path: string) => request.Test;
  delete: (path: string) => request.Test;
}

// calls the real server as whoever holds the token, e.g. withToken(token).get('/api/games/mine')
export function withToken(token: string): SignedInApi {
  const api = request(createApp());
  const authorization = `Bearer ${token}`;
  return {
    get: (path: string) => api.get(path).set('Authorization', authorization),
    post: (path: string) => api.post(path).set('Authorization', authorization),
    put: (path: string) => api.put(path).set('Authorization', authorization),
    patch: (path: string) => api.patch(path).set('Authorization', authorization),
    delete: (path: string) => api.delete(path).set('Authorization', authorization),
  };
}

// a valid body for creating or editing a game, with overrides for what the test is about
export function validGameBody(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    title: 'New Title',
    sport: 'basketball',
    starts_at: new Date(Date.now() + ONE_DAY_MS).toISOString(),
    location_name: 'UBC Rec Centre',
    address: '6000 Student Union Blvd',
    latitude: 49.26,
    longitude: -123.25,
    max_players: 10,
    skill_level: 'any',
    ...overrides,
  };
}
