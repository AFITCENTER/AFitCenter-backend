package br.com.afitcenter.paciente.repository;

import br.com.afitcenter.paciente.model.entity.CodigoAcesso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Chave = id da ficha. */
public interface CodigoAcessoRepository extends JpaRepository<CodigoAcesso, UUID> {

    Optional<CodigoAcesso> findByCodigoHash(String codigoHash);
}
