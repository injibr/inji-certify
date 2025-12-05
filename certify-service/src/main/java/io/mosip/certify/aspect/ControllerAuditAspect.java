package io.mosip.certify.aspect;

import io.mosip.certify.config.AuditConfig;
import io.mosip.certify.core.dto.CredentialRequest;
import io.mosip.certify.services.CertifyAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ControllerAuditAspect {

    private final CertifyAuditService auditService;
    private final HttpServletRequest request;
    private final AuditConfig auditConfig;

    public ControllerAuditAspect(CertifyAuditService auditService,
                                 HttpServletRequest request,
                                 AuditConfig auditConfig) {
        this.auditService = auditService;
        this.request = request;
        this.auditConfig = auditConfig;
    }

    @Around("execution(* io.mosip.certify.controller.VCIssuanceController.getCredential(..))")
    public Object auditController(ProceedingJoinPoint joinPoint) throws Throwable {

        // If audit disabled → just proceed without logging anything
        if (!auditConfig.isAuditEnabled()) {
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        String vcType = "UNKNOWN";

        for (Object arg : args) {
            if (arg instanceof CredentialRequest request) {
                vcType = request.getDoctype();
            }
        }

        String issuedBy = request.getRemoteUser() != null ? request.getRemoteUser() : "anonymous";

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            log.error("Exception in controller: {}", e.getMessage());
            log.info("Audit entry created for Cpf: {} | vcIssued=false and Credential type: {}", issuedBy, vcType);
            auditService.logAudit(vcType, false, issuedBy);
            throw e;
        }

        auditService.logAudit(vcType, true, issuedBy);
        log.info("Audit updated for cpf: {} | vcIssued=true and Credential type: {}", issuedBy, vcType);
        return result;
    }
}
