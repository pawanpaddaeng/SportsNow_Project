// setup shared by the unmocked User tests

/*  the profile GET and PUT /api/users/me send back for a user addUser saved, with overrides for
    what the test changed. created_at is when the test ran, so any text is accepted
 */
export function expectedProfile(userId: number, firstName: string, overrides: Record<string, unknown> = {}) {
  return {
    user_id: userId,
    email: 'no-mock-test@example.com',
    f_name: firstName,
    l_name: 'Tester',
    birthYear: 1995,
    created_at: expect.any(String) as unknown,
    ...overrides,
  };
}
