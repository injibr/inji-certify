package io.mosip.certify.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@Entity
@NoArgsConstructor
@Table(name = "certify_keys")
public class CertifyKeys {
    @Id
    @Column(name = "config_key")
    private String key;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String value;
}
