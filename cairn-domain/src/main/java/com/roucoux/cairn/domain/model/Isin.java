package com.roucoux.cairn.domain.model;

import java.util.regex.Pattern;

public final class Isin {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private Isin() {}

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }
}
