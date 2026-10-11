package br.com.afitcenter.autenticacao.repository;

import br.com.afitcenter.autenticacao.model.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /** O e-mail é gravado normalizado; normalize também o valor de busca. */
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
