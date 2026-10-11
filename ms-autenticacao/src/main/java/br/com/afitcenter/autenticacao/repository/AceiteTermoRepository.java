package br.com.afitcenter.autenticacao.repository;

import br.com.afitcenter.autenticacao.model.entity.AceiteTermo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AceiteTermoRepository extends JpaRepository<AceiteTermo, UUID> {

    List<AceiteTermo> findAllByUsuarioId(UUID usuarioId);
}
