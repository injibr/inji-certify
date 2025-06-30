package io.mosip.certify.repository;

import io.mosip.certify.entity.CertifyKeys;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface ConfigurationRepository extends JpaRepository<CertifyKeys, String> {
    Optional<CertifyKeys> findByKey(String key);
}