package io.mosip.certify.repository;


import io.mosip.certify.entity.CertifyAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CertifyAuditRepository extends JpaRepository<CertifyAudit, UUID> {

    // Find all audits for a specific VC type
    List<CertifyAudit> findByVcType(String vcType);

    // Find all audits issued by a specific user
    List<CertifyAudit> findByIssuedBy(String issuedBy);

    // Find audits by issued status
    List<CertifyAudit> findByVcIssued(boolean vcIssued);

    // Find all issued audits between two dates
    List<CertifyAudit> findByIssuedDateBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);

    // Optional: fetch latest audits first
    List<CertifyAudit> findAllByOrderByCreatedDateDesc();
}