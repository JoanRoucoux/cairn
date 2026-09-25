package com.roucoux.cairn.application.exception;

public class LastPasskeyException extends RuntimeException {

    public LastPasskeyException() {
        super("Cannot revoke the last passkey: it is the only way to sign in");
    }
}
