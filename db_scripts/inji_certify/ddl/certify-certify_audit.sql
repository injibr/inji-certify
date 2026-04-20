-- INJIBR-CUSTOM: Audit table for VC issuance events
-- -------------------------------------------------------------------------------------------------
-- Database Name: inji_certify
-- Table Name : certify_audit
-- Purpose    : Audit trail for credential issuance
-- -------------------------------------------------------------------------------------------------

CREATE TABLE certify_audit (
    id UUID NOT NULL,
    vc_type VARCHAR NOT NULL,
    vc_issued BOOLEAN NOT NULL,
    issued_by VARCHAR NOT NULL,
    created_date TIMESTAMP,
    issued_date TIMESTAMP,
    CONSTRAINT pk_certify_audit PRIMARY KEY (id)
);

COMMENT ON TABLE certify_audit IS 'Certify Audit: Audit trail for credential issuance events.';
COMMENT ON COLUMN certify_audit.id IS 'Audit ID: Unique identifier for the audit record.';
COMMENT ON COLUMN certify_audit.vc_type IS 'VC Type: Type of the verifiable credential issued.';
COMMENT ON COLUMN certify_audit.vc_issued IS 'VC Issued: Whether the credential was successfully issued.';
COMMENT ON COLUMN certify_audit.issued_by IS 'Issued By: Subject (CPF) of the credential holder.';
COMMENT ON COLUMN certify_audit.created_date IS 'Created Date: Date and time the audit record was created.';
COMMENT ON COLUMN certify_audit.issued_date IS 'Issued Date: Date and time the credential was issued.';
