package com.roucoux.cairn.events;

import org.apache.avro.util.ClassSecurityValidator;

public final class TrustedEventClasses {

    private static final String EVENTS_PACKAGE = "com.roucoux.cairn.events";

    private TrustedEventClasses() {}

    public static synchronized void register() {
        if (ClassSecurityValidator.getGlobal().isTrusted(PriceUpdatedEvent.class)) {
            return;
        }
        ClassSecurityValidator.setGlobal(ClassSecurityValidator.composite(
                ClassSecurityValidator.getGlobal(),
                type -> type.getPackageName().equals(EVENTS_PACKAGE)));
    }
}
