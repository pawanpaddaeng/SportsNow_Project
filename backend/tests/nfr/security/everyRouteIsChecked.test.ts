import { createApp } from '../../../src/app';
import { listRegisteredRoutes, PROTECTED_ROUTES, PUBLIC_ROUTES, routeName } from './securityHelpers';

/*  NFR 1 (Security): every request except sign up and login must carry a valid token from
    the same account. badSignInsAreRejected.test.ts sends bad tokens to every route in
    PROTECTED_ROUTES. This test makes sure that list is complete: a route added to the app
    without being added to the list fails here, so no route is left unchecked.
 */

// Interface: every route registered on the Express app
describe('NFR 1: every route is checked for sign in', () => {
  // Input: the routes the app really registered
  // Expected status code: none, no request is sent
  // Expected behavior: nothing changes
  // Expected output: the registered routes are exactly the public ones (sign up, login,
  //   health) and the ones in PROTECTED_ROUTES
  test('Every route except sign up, login and health is in the list the security tests check', () => {
    // 1. the routes the app really has
    const registered = listRegisteredRoutes(createApp());

    // 2. check they're exactly the public routes plus the protected ones the tests check
    const known = [...PUBLIC_ROUTES, ...PROTECTED_ROUTES].map(routeName).sort();
    expect(registered).toEqual(known);
  });
});
