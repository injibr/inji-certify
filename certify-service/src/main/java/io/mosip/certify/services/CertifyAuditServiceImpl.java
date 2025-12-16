package io.mosip.certify.services;

import io.mosip.certify.entity.CertifyAudit;
import io.mosip.certify.repository.CertifyAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class CertifyAuditServiceImpl implements CertifyAuditService{
    private final CertifyAuditRepository repository;

    public CertifyAuditServiceImpl(CertifyAuditRepository repository) {
        this.repository = repository;
    }

    public void logAudit(String vcType, boolean vcIssued, String issuedBy) {
        CertifyAudit audit = new CertifyAudit();
        audit.setVcType(vcType);
        audit.setVcIssued(vcIssued);
        audit.setIssuedBy(issuedBy);
        audit.setCreatedDate(LocalDateTime.now());
        if (vcIssued) audit.setIssuedDate(LocalDateTime.now());
        repository.save(audit);
    }

    public List<CertifyAudit> getAuditsByType(String vcType) {
        return repository.findByVcType(vcType);
    }

    public List<CertifyAudit> getRecentAudits() {
        return repository.findAllByOrderByCreatedDateDesc();
    }

    @Transactional
    public CertifyAudit updateAudit(CertifyAudit audit) {
        return repository.save(audit);
    }
}
