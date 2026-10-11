package br.com.afitcenter.dieta.repository;

import br.com.afitcenter.dieta.model.entity.AlimentoMedida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlimentoMedidaRepository extends JpaRepository<AlimentoMedida, Integer> {

    List<AlimentoMedida> findAllByAlimentoIdOrderByGramasAscDescricaoAsc(Integer alimentoId);

    Optional<AlimentoMedida> findByIdAndAlimentoId(Integer id, Integer alimentoId);

    boolean existsByAlimentoIdAndDescricaoIgnoreCaseAndIdNot(Integer alimentoId, String descricao, Integer ignorarId);

    /** A medida está em algum item ou substituto (de qualquer dieta, inclusive excluída)? */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM refeicao_item WHERE alimento_medida_id = :id)
                OR EXISTS (SELECT 1 FROM refeicao_item_substituto WHERE alimento_medida_id = :id)
            """, nativeQuery = true)
    boolean emUso(@Param("id") Integer id);
}
