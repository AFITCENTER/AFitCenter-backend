package br.com.afitcenter.pessoa.model.entity;

import br.com.afitcenter.pessoa.model.enums.TipoTermo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Registro de aceite dos termos (LGPD): qual versão, quando e de onde. */
@Getter
@Entity
@Table(name = "aceite_termo")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AceiteTermo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pessoa_id", nullable = false)
    private UUID pessoaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTermo tipo;

    @Column(nullable = false)
    private String versao;

    @Column(name = "aceito_em", nullable = false)
    private Instant aceitoEm;

    @Column(length = 45)
    private String ip;

    public AceiteTermo(UUID pessoaId, TipoTermo tipo, String versao, String ip) {
        this.pessoaId = pessoaId;
        this.tipo = tipo;
        this.versao = versao;
        this.ip = ip;
        this.aceitoEm = Instant.now();
    }
}
