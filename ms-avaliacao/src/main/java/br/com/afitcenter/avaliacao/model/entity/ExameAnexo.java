package br.com.afitcenter.avaliacao.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadados de um arquivo do exame: o arquivo fica no storage (S3, MinIO...) e aqui só a chave.
 * Imutável e sem {@code atualizado_em}, por isso não estende {@code EntidadeAuditavel}.
 */
@Getter
@Entity
@Table(name = "exame_anexo")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExameAnexo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exame_id", nullable = false, updatable = false)
    private UUID exameId;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "nome_arquivo", nullable = false, updatable = false)
    private String nomeArquivo;

    @Column(name = "mime_type", nullable = false, updatable = false)
    private String mimeType;

    @Column(name = "tamanho_bytes", updatable = false)
    private Long tamanhoBytes;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public ExameAnexo(UUID exameId, String storageKey, String nomeArquivo, String mimeType, Long tamanhoBytes) {
        this.exameId = exameId;
        this.storageKey = storageKey;
        this.nomeArquivo = nomeArquivo;
        this.mimeType = mimeType;
        this.tamanhoBytes = tamanhoBytes;
        this.criadoEm = Instant.now();
    }
}
