package com.roucoux.cairn.application.exception;

/**
 * A passkey is the sole authentication factor Cairn has: revoking the last one would lock the
 * owner out permanently, with no password to fall back on.
 */
public class LastPasskeyException extends RuntimeException {

    public LastPasskeyException() {
        super("Cannot revoke the last passkey: it is the only way to sign in");
    }
}
