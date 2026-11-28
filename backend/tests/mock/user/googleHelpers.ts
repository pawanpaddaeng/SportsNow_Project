import { LoginTicket, OAuth2Client, type TokenPayload } from 'google-auth-library';

// setup shared by the mocked sign up and login tests

// the Google account the mocked Google check says signed the ID token
export const GOOGLE_ACCOUNT = { sub: 'google-new-user', email: 'new.user@example.com' };

/*  replaces Google's check of the ID token, so any token is accepted as signed by
    GOOGLE_ACCOUNT. The real check needs a token Google signed for this app. account changes
    what Google says about the account: { email_verified: false }, or { noEmail: true } for an
    account with no email. null makes the check accept the token but say nothing about the account
 */
export function mockGoogleCheck(account: { email_verified?: boolean; noEmail?: boolean } | null = {}): void {
  let payload: TokenPayload | undefined;
  if (account !== null) {
    const { noEmail, ...changes } = account;
    payload = {
      iss: 'https://accounts.google.com',
      aud: 'test-client-id',
      iat: 0,
      exp: 0,
      email_verified: true,
      ...GOOGLE_ACCOUNT,
      ...changes,
    };
    if (noEmail === true) delete payload.email;
  }
  const ticket = new LoginTicket('test-envelope', payload);
  // verifyIdToken also has an older callback form that returns nothing, and jest.spyOn types
  // the spy from that one. The routes use the form that returns a promise, so the spy is typed as that
  const verifyIdToken = jest.spyOn(OAuth2Client.prototype, 'verifyIdToken') as unknown as
    jest.SpyInstance<Promise<LoginTicket>>;
  verifyIdToken.mockResolvedValue(ticket);
}
