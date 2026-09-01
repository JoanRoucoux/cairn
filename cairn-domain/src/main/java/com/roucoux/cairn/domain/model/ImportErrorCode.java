package com.roucoux.cairn.domain.model;

/**
 * The vocabulary of import failures, whatever format carried the rows. A code travels to the
 * caller instead of a sentence, so the wording belongs to whoever displays it and can be
 * translated; the server only ever states what went wrong, never how to say it.
 */
public enum ImportErrorCode {
    /** The first line is not the expected header. */
    BAD_HEADER,
    /** The row does not carry every column the header announces. */
    WRONG_COLUMN_COUNT,
    /** The account type is not one of {@link AccountType}. */
    UNKNOWN_ACCOUNT_TYPE,
    /** A numeric column does not hold a number. */
    NOT_A_NUMBER,
    /** A position of zero: removing a holding is a deletion, not an import. */
    ZERO_QUANTITY,
    /** No price source recognises the instrument's identifier. */
    UNRESOLVED_INSTRUMENT
}
