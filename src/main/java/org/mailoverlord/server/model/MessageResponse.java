package org.mailoverlord.server.model;

/**
 * Response returned from the delete operation.
 *
 * <p>Delete is a single transaction, so it is genuinely all or nothing and a flat flag describes
 * it honestly. Release cannot be described this way, since each message is delivered separately to
 * a real address; it answers with {@link MessageReleaseResponse} instead.
 */
public class MessageResponse {

    private boolean successful = true;
    private String errorMessage;

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
