import { LoginTicket, OAuth2Client } from 'google-auth-library';

// setup shared by the mocked sign up and login tests

// the Google account the mocked Google check says signed the ID token
export const GOOGLE_ACCOUNT = { sub: 'google-new-user', email: 'new.user@example.com' };

/*  replaces Google's check of the ID token, so any token is accepted as signed by
    GOOGLE_ACCOUNT. The real check needs a token Google signed for this app
 */
export function mockGoogleCheck(): void {
  const ticket = new LoginTicket('test-envelope', {
    iss: 'https://accounts.google.com',
    aud: 'test-client-id',
    iat: 0,
    exp: 0,
    email_verified: true,
    ...GOOGLE_ACCOUNT,
  });
  jest.spyOn(OAuth2Client.prototype, 'verifyIdToken').mockImplementation(() => Promise.resolve(ticket));
}
