package com.nagriksetu.complaint;

public enum WardMatch {
    /** The location fell inside exactly one configured ward boundary. */
    AUTO_MATCHED,
    /** No reliable boundary match: an administrator must confirm the ward. */
    PENDING_VERIFICATION,
    /** An administrator confirmed the ward manually. */
    VERIFIED
}
