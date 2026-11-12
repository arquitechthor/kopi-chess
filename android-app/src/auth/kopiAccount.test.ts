import { describe, expect, it } from 'vitest';
import { failureOf, resultOf } from './kopiAccount';

describe('resultOf', () => {
  it('is signed in only when Amplify says so', () => {
    expect(resultOf({ isSignedIn: true })).toEqual({ kind: 'signedIn' });
  });

  it('sends any other next step to the Kopi web', () => {
    expect(resultOf({ isSignedIn: false })).toEqual({ kind: 'finishOnWeb' });
  });
});

describe('failureOf', () => {
  it('maps the Cognito errors the login screen explains', () => {
    expect(failureOf({ name: 'NotAuthorizedException' })).toBe('wrongCredentials');
    expect(failureOf({ name: 'UserNotFoundException' })).toBe('wrongCredentials');
    expect(failureOf({ name: 'UserNotConfirmedException' })).toBe('notConfirmed');
    expect(failureOf({ name: 'TooManyRequestsException' })).toBe('tooManyAttempts');
    expect(failureOf({ name: 'NetworkError' })).toBe('network');
  });

  it('is unknown for anything else', () => {
    expect(failureOf({ name: 'SomethingNew' })).toBe('unknown');
    expect(failureOf(new Error('boom'))).toBe('unknown');
    expect(failureOf(null)).toBe('unknown');
    expect(failureOf('text')).toBe('unknown');
  });
});
