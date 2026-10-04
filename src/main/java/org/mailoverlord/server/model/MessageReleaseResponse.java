package org.mailoverlord.server.model;

import java.util.List;

/**
 * The result of releasing a batch of messages.
 *
 * <p>Release is the one operation here that can partly succeed, because each message is delivered
 * individually and delivery reaches a real inbox. A single flat success flag hid that: the response
 * said the whole batch failed even when messages 1 and 2 had already gone out, so the obvious
 * response of fixing the problem and retrying re-sent them. {@link #outcomes()} is what makes a
 * partial result visible.
 *
 * <p>The flat fields are kept alongside it so a client can still ask the simple question. When
 * {@code successful} is false, {@link #errorMessage} says how far the batch got rather than merely
 * that something went wrong.
 *
 * @param successful whether every requested message was released
 * @param errorMessage a summary of what went wrong, or {@code null} when everything was released
 * @param outcomes one entry per requested id, in the order requested
 */
public record MessageReleaseResponse(
        boolean successful,
        String errorMessage,
        List<MessageReleaseOutcome> outcomes) {

    public MessageReleaseResponse {
        outcomes = List.copyOf(outcomes);
    }

    /**
     * Summarises a completed batch. A caller reading only {@code successful} still learns that some
     * messages went out, which is the fact that makes a blind retry dangerous.
     */
    public static MessageReleaseResponse of(List<MessageReleaseOutcome> outcomes) {
        long released = outcomes.stream().filter(MessageReleaseOutcome::released).count();
        if (released == outcomes.size()) {
            return new MessageReleaseResponse(true, null, outcomes);
        }
        int total = outcomes.size();
        return new MessageReleaseResponse(false,
                "Released %d of %d messages; %d failed.".formatted(released, total, total - released),
                outcomes);
    }

    /**
     * The batch failed before any of it ran, so every requested id is reported as unreleased
     * rather than being left out of the accounting.
     */
    public static MessageReleaseResponse noneReleased(List<Long> ids, String errorMessage) {
        return new MessageReleaseResponse(false, errorMessage,
                ids.stream().map(id -> new MessageReleaseOutcome(id, false, errorMessage)).toList());
    }
}