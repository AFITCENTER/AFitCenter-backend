package br.com.afitcenter.pessoa.repository;

import br.com.afitcenter.pessoa.model.entity.TokenEmail;
import br.com.afitcenter.pessoa.model.enums.FinalidadeToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TokenEmailRepository extends JpaRepository<TokenEmail, UUID> {

    /** Trava a linha ({@code FOR UPDATE}): dois usos simultâneos do mesmo link não passam juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenEmail> findByTokenHashAndFinalidade(String tokenHash, FinalidadeToken finalidade);

    @Modifying
    @Query("delete from TokenEmail t where t.pessoaId = :pessoaId and t.finalidade = :finalidade")
    void apagar(@Param("pessoaId") UUID pessoaId, @Param("finalidade") FinalidadeToken finalidade);
}
