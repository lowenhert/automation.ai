package com.lowenhert.patchmanagement.core.ai;

import lombok.Getter;

/** A failure talking to the LLM provider, classified so callers and the API can react appropriately. */
@Getter
public class AiProviderException extends RuntimeException {

    public enum Reason {
        CONNECTION_FAILED,
        TIMEOUT,
        MODEL_NOT_FOUND,
        UNAUTHORIZED,
        PROVIDER_ERROR,
        INVALID_RESPONSE,
        INVALID_OUTPUT
    }

    private final Reason reason;

    public AiProviderException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiProviderException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
