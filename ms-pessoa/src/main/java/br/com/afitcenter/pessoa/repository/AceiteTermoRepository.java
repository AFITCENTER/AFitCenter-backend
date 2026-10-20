package br.com.afitcenter.pessoa.repository;

import br.com.afitcenter.pessoa.model.entity.AceiteTermo;
import br.com.afitcenter.pessoa.model.enums.TipoTermo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AceiteTermoRepository extends JpaRepository<AceiteTermo, UUID> {

    List<AceiteTermo> findAllByPessoaId(UUID pessoaId);

    boolean existsByPessoaIdAndTipoAndVersao(UUID pessoaId, TipoTermo tipo, String versao);
}
