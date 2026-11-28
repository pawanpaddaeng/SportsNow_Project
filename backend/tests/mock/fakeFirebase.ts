/*  stand-in for firebase-admin/app and firebase-admin/messaging in mocked tests, so our push
    code (src/core/notifications/push.ts) runs without a service account key or real phones.
    A test file swaps it in for both modules:
      jest.mock('firebase-admin/app', () => jest.requireActual('../fakeFirebase'));
      jest.mock('firebase-admin/messaging', () => jest.requireActual('../fakeFirebase'));
    and sets what Firebase answers through fakeFirebase. Call fakeFirebase.reset() first in each
    test that uses it
 */
export const fakeFirebase = {
  // the Firebase apps started so far, initializeApp adds one
  apps: [] as unknown[],
  initializeApp: jest.fn(),
  sendEachForMulticast: jest.fn(),
  reset(): void {
    this.apps = [];
    this.initializeApp.mockReset();
    this.sendEachForMulticast.mockReset();
  },
};

export function getApps(): unknown[] {
  return fakeFirebase.apps;
}

export function initializeApp(options: unknown): void {
  fakeFirebase.initializeApp(options);
  fakeFirebase.apps.push({});
}

export function applicationDefault(): string {
  return 'default-credential';
}

export function getMessaging(): { sendEachForMulticast: (message: unknown) => unknown } {
  return { sendEachForMulticast: (message: unknown) => fakeFirebase.sendEachForMulticast(message) };
}
