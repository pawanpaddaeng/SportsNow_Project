import request from 'supertest';

import { createApp } from '../src/app';

// setup shared by the mocked and unmocked tests

export const ONE_DAY_MS = 24 * 60 * 60 * 1000;

// the real server, for tests to call
export const api = request(createApp());

// the header that signs a call in
export type AuthHeader = Record<string, string>;

// signs a call in as whoever holds the token, e.g. api.get('/api/games/mine').set(withToken(token))
export function withToken(token: string): AuthHeader {
  return { Authorization: `Bearer ${token}` };
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
