package org.mailoverlord.server.model;

/**
 * What a release batch ended as, recorded for every attempt.
 *
 * <p>One outcome per {@code release} call rather than per requested id: the audit trail is about
 * what the operator asked for and what happened overall, and the per-id detail lives in the API
 * response. {@code PARTIAL} is the interesting case, which is why the issue calls it out as the
 * one worth replaying: some messages went out, so retrying the whole batch would re-send them.
 */
public enum ReleaseOutcome {

    /**
     * Every requested message was released.
     */
    RELEASED,

    /**
     * Some were released and some were not.
     */
    PARTIAL,

    /**
     * None were released.
     */
    FAILED
}