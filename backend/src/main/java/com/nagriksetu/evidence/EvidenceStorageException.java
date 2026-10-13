package com.nagriksetu.evidence;

import com.nagriksetu.common.ApiException;
import org.springframework.http.HttpStatus;

/** Storage failures are retryable from the client's point of view. */
public class EvidenceStorageException extends ApiException {

    public EvidenceStorageException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", message + ". Please try again.");
        initCause(cause);
    }
}
