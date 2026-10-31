// stand-in for src/core/config/env in mocked tests: with every database call mocked the
// server needs no real settings. Each test file swaps it in with
//   jest.mock('../../../src/core/config/env', () => jest.requireActual('../mockEnv'));
// (nothing is imported here, since it loads before the test file's imports)
export const env = {
  port: 3000,
  mysqlUrl: 'mysql://test:test@localhost:3306/test',
  mysqlUser: 'test',
  mysqlPassword: 'test',
  mysqlDatabase: 'test',
  jwtSecret: 'test-jwt-secret',
};
