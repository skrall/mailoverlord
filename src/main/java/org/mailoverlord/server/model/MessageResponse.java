package org.mailoverlord.server.model;

/**
 * Response returned from MessageDelete and MessageRequest operations.
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
