package br.com.afitcenter.planos.repository;

import br.com.afitcenter.planos.model.entity.Fatura;
import br.com.afitcenter.planos.model.enums.Enums.StatusFatura;
import br.com.afitcenter.planos.model.enums.Enums.TipoFatura;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Nas rotas do nutricionista, filtre sempre por {@code nutricionistaId}. */
public interface FaturaRepository extends JpaRepository<Fatura, UUID> {

    Optional<Fatura> findByIdAndNutricionistaId(UUID id, UUID nutricionistaId);

    Page<Fatura> findAllByNutricionistaId(UUID nutricionistaId, Pageable pageable);

    List<Fatura> findAllByAssinaturaIdAndTipoAndStatus(UUID assinaturaId, TipoFatura tipo, StatusFatura status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Fatura f WHERE f.id = :id")
    Optional<Fatura> bloquear(@Param("id") UUID id);

    /** Renovações cuja cobrança não foi criada (o ms-pagamentos estava fora do ar na emissão). */
    @Query(value = """
            SELECT * FROM fatura
            WHERE tipo = 'RENOVACAO' AND status = 'ABERTA' AND cobranca_id IS NULL AND atualizado_em < :antesDe
            ORDER BY criado_em
            LIMIT :lote
            """, nativeQuery = true)
    List<Fatura> renovacoesSemCobranca(@Param("antesDe") Instant antesDe, @Param("lote") int lote);
}
