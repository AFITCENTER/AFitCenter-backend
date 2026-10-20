package br.com.afitcenter.pessoa.repository;

import br.com.afitcenter.pessoa.model.entity.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {

    /** O e-mail é gravado normalizado; normalize também o valor de busca. */
    Optional<Pessoa> findByEmail(String email);

    boolean existsByCpf(String cpf);
}
