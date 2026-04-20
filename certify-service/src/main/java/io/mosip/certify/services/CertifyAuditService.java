package io.mosip.certify.services;

import io.mosip.certify.entity.CertifyAudit;
import java.util.List;

public interface CertifyAuditService {
    void logAudit(String vcType, boolean vcIssued, String issuedBy);

    List<CertifyAudit> getAuditsByType(String vcType);

    List<CertifyAudit> getRecentAudits() ;

    CertifyAudit updateAudit(CertifyAudit audit);
}
