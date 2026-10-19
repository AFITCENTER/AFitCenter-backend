package br.com.afitcenter.planos.repository;

import br.com.afitcenter.planos.model.entity.Assinatura;
import br.com.afitcenter.planos.model.enums.Enums.StatusAssinatura;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Uma assinatura por nutricionista: busque sempre por {@code nutricionistaId} nas rotas do nutricionista. */
public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID>, JpaSpecificationExecutor<Assinatura> {

    Optional<Assinatura> findByNutricionistaId(UUID nutricionistaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Assinatura a WHERE a.id = :id")
    Optional<Assinatura> bloquear(@Param("id") UUID id);

    /** Cria no plano padrão sem erro quando duas requisições chegam juntas para o mesmo nutricionista. */
    @Modifying
    @Query(value = """
            INSERT INTO assinatura (id, nutricionista_id, plano_id) VALUES (:id, :nutricionistaId, :planoId)
            ON CONFLICT (nutricionista_id) DO NOTHING
            """, nativeQuery = true)
    int inserirSeNaoExistir(@Param("id") UUID id, @Param("nutricionistaId") UUID nutricionistaId,
                            @Param("planoId") Integer planoId);

    /** Planos pagos em dia, sem cancelamento agendado, perto do fim do período e ainda sem fatura de renovação. */
    @Query(value = """
            SELECT a.id FROM assinatura a
            JOIN plano p ON p.id = a.plano_id
            WHERE a.status = 'ATIVA' AND a.plano_agendado_id IS NULL AND p.preco_mensal > 0
              AND a.periodo_fim IS NOT NULL AND a.periodo_fim <= :limite
              AND NOT EXISTS (SELECT 1 FROM fatura f
                              WHERE f.assinatura_id = a.id AND f.tipo = 'RENOVACAO' AND f.status <> 'CANCELADA'
                                AND f.periodo_inicio = a.periodo_fim)
            ORDER BY a.periodo_fim
            LIMIT :lote
            """, nativeQuery = true)
    List<UUID> paraRenovar(@Param("limite") Instant limite, @Param("lote") int lote);

    /** Período encerrado com algo a fazer: cancelamento agendado, entrar em inadimplência ou ser suspensa. */
    @Query(value = """
            SELECT a.id FROM assinatura a
            WHERE a.periodo_fim IS NOT NULL AND a.periodo_fim <= :agora
              AND (a.plano_agendado_id IS NOT NULL
                   OR a.status = 'ATIVA'
                   OR (a.status = 'INADIMPLENTE' AND a.periodo_fim <= :fimCarencia))
            ORDER BY a.periodo_fim
            LIMIT :lote
            """, nativeQuery = true)
    List<UUID> vencidas(@Param("agora") Instant agora, @Param("fimCarencia") Instant fimCarencia,
                        @Param("lote") int lote);

    /** Assinaturas cujos limites efetivos vêm do plano (suspensas usam os do plano padrão). */
    @Query(value = "SELECT id FROM assinatura WHERE plano_id = :planoId AND status <> 'SUSPENSA'", nativeQuery = true)
    List<UUID> usandoLimitesDe(@Param("planoId") Integer planoId);

    @Query(value = "SELECT id FROM assinatura WHERE plano_id = :planoId OR status = 'SUSPENSA'", nativeQuery = true)
    List<UUID> usandoLimitesDoPadrao(@Param("planoId") Integer planoId);

    /** Nova versão sem mudar dados: os limites do plano mudaram e as réplicas precisam do evento. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE assinatura SET versao = versao + 1 WHERE id IN (:ids)", nativeQuery = true)
    int incrementarVersao(@Param("ids") Collection<UUID> ids);

    final class Filtros {

        private Filtros() {
        }

        public static Specification<Assinatura> comStatus(StatusAssinatura status) {
            return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
        }

        public static Specification<Assinatura> doPlano(Integer planoId) {
            return planoId == null ? null : (root, query, cb) -> cb.equal(root.get("plano").get("id"), planoId);
        }
    }
}
