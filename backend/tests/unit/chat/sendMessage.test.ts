import { MAX_MESSAGE_LENGTH, parseSendMessage } from '../../../src/chat/validation/sendMessage';

// a send_message request as the app sends it, with overrides for what the test is about
function request(overrides: Record<string, unknown> = {}): string {
  return JSON.stringify({ type: 'send_message', chat_id: 7, text: 'Hi', client_id: 'c-1', ...overrides });
}

// how a send_message request from the WebSocket is checked
describe('parseSendMessage', () => {
  test('A valid request is accepted with its text trimmed', () => {
    expect(parseSendMessage(request({ text: '  Hi there  ' }))).toEqual({
      ok: true,
      value: { chatId: 7, text: 'Hi there', clientId: 'c-1' },
    });
  });

  test.each([
    ['missing', undefined],
    ['a number', 12],
    ['null', null],
  ])('client_id that is %s becomes null', (_case, clientId) => {
    expect(parseSendMessage(request({ client_id: clientId }))).toEqual({
      ok: true,
      value: { chatId: 7, text: 'Hi', clientId: null },
    });
  });

  test.each([
    ['not JSON', 'hello'],
    ['JSON null', 'null'],
    ['a JSON string', '"send_message"'],
    ['a JSON number', '7'],
  ])('Request that is %s is rejected', (_case, raw) => {
    expect(parseSendMessage(raw)).toEqual({ ok: false, error: 'Request must be a JSON object' });
  });

  test.each([
    ['missing', undefined],
    ['another type', 'typing'],
  ])('type that is %s is rejected', (_case, type) => {
    expect(parseSendMessage(request({ type }))).toEqual({ ok: false, error: 'type must be send_message' });
  });

  test.each([
    ['missing', undefined],
    ['a string', '7'],
    ['zero', 0],
    ['negative', -3],
    ['a decimal', 1.5],
    ['above the largest id', 4294967296],
  ])('chat_id that is %s is rejected', (_case, chatId) => {
    expect(parseSendMessage(request({ chat_id: chatId }))).toEqual({
      ok: false,
      error: 'chat_id must be a positive whole number',
    });
  });

  test.each([
    ['missing', undefined],
    ['not a string', 42],
    ['empty', ''],
    ['only spaces', '   \n\t '],
  ])('text that is %s is rejected', (_case, text) => {
    expect(parseSendMessage(request({ text }))).toEqual({ ok: false, error: 'text must not be empty' });
  });

  test('Text of 1000 characters is accepted, outer spaces don\'t count', () => {
    const longest = 'a'.repeat(MAX_MESSAGE_LENGTH);

    expect(parseSendMessage(request({ text: ` ${longest} ` }))).toEqual({
      ok: true,
      value: { chatId: 7, text: longest, clientId: 'c-1' },
    });
  });

  test('Text of 1001 characters is rejected', () => {
    expect(parseSendMessage(request({ text: 'a'.repeat(MAX_MESSAGE_LENGTH + 1) }))).toEqual({
      ok: false,
      error: 'text must be at most 1000 characters',
    });
  });
});
