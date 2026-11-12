// The Kopi account for the app: Amplify Auth with SRP against the shared Kopi
// user pool, with the app's own client (docs/protocolo-rele.md §8). Public
// values: a client with no secret, like the other Kopi apps.

import { Amplify } from 'aws-amplify';
import { fetchAuthSession, signIn, signOut, type SignInOutput } from 'aws-amplify/auth';

export const KOPI_COGNITO = {
  userPoolId: 'eu-south-2_Yunp5ZGUR',
  userPoolClientId: '6kgqvf70nuvmfc89obppl2t398', // kopi-chess-mobile-client
} as const;

/** What a sign-in attempt ended in, as the login screen shows it. */
export type SignInResult =
  | { kind: 'signedIn' }
  | { kind: 'finishOnWeb' } // the account needs a step the app does not do (confirm, new password...)
  | { kind: 'failed'; reason: SignInFailure };

export type SignInFailure = 'wrongCredentials' | 'notConfirmed' | 'tooManyAttempts' | 'network' | 'unknown';

/** The port the screens use; the Amplify one below, a fake in the tests. */
export interface KopiAccount {
  signIn(email: string, password: string): Promise<SignInResult>;
  signOut(): Promise<void>;
  /** The signed-in user's e-mail, or null when nobody is signed in. */
  currentEmail(): Promise<string | null>;
  /** A valid access token (refreshed if needed), for the relay; null when signed out. */
  accessToken(): Promise<string | null>;
}

/** Only DONE means signed in; any other next step is completed on the Kopi web. */
export function resultOf(output: Pick<SignInOutput, 'isSignedIn'>): SignInResult {
  return output.isSignedIn ? { kind: 'signedIn' } : { kind: 'finishOnWeb' };
}

const FAILURES: Record<string, SignInFailure> = {
  NotAuthorizedException: 'wrongCredentials',
  UserNotFoundException: 'wrongCredentials',
  UserNotConfirmedException: 'notConfirmed',
  LimitExceededException: 'tooManyAttempts',
  TooManyRequestsException: 'tooManyAttempts',
  TooManyFailedAttemptsException: 'tooManyAttempts',
  NetworkError: 'network',
};

export function failureOf(error: unknown): SignInFailure {
  const name = (error as { name?: unknown } | null)?.name;
  return (typeof name === 'string' && FAILURES[name]) || 'unknown';
}

export function configureKopiAccount(): void {
  Amplify.configure({ Auth: { Cognito: KOPI_COGNITO } });
}

export const amplifyKopiAccount: KopiAccount = {
  async signIn(email, password) {
    try {
      // Amplify refuses a sign-in while someone is already signed in.
      await signOut();
      return resultOf(await signIn({ username: email.trim(), password }));
    } catch (error) {
      return { kind: 'failed', reason: failureOf(error) };
    }
  },

  async signOut() {
    await signOut();
  },

  async currentEmail() {
    const session = await fetchAuthSession().catch(() => null);
    const email = session?.tokens?.idToken?.payload?.['email'];
    return session?.tokens && typeof email === 'string' ? email : null;
  },

  async accessToken() {
    const session = await fetchAuthSession().catch(() => null);
    return session?.tokens?.accessToken?.toString() ?? null;
  },
};
