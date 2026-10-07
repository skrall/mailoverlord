package org.mailoverlord.server.repositories;

import org.mailoverlord.server.entities.ReleaseAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The release audit trail.
 */
public interface ReleaseAuditRepository extends JpaRepository<ReleaseAudit, Long> {
}