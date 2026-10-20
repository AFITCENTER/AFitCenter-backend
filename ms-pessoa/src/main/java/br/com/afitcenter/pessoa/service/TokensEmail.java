package br.com.afitcenter.pessoa.service;

import br.com.afitcenter.pessoa.model.entity.TokenEmail;
import br.com.afitcenter.pessoa.model.enums.FinalidadeToken;
import br.com.afitcenter.pessoa.repository.TokenEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Tokens dos links enviados por e-mail: 32 bytes aleatórios (256 bits) em Base64 URL, sem como adivinhar nem
 * testar em sequência. O banco guarda só o SHA-256.
 *
 * <p>Um token novo substitui os anteriores da mesma finalidade, e usar um apaga todos eles. Dois pedidos
 * simultâneos podem deixar dois links válidos, que deixam de valer juntos quando um deles é usado.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TokensEmail {

    private static final int BYTES = 32;

    private final TokenEmailRepository repository;
    private final SecureRandom aleatorio = new SecureRandom();

    /** Devolve o token em claro, que só vai no e-mail. */
    public String emitir(UUID pessoaId, FinalidadeToken finalidade, Duration validade) {
        repository.apagar(pessoaId, finalidade);
        byte[] bytes = new byte[BYTES];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new TokenEmail(pessoaId, finalidade, hash(token), Instant.now().plus(validade)));
        return token;
    }

    /**
     * Pessoa dona do token, se ele existe, é desta finalidade e não venceu. O token e os outros da mesma finalidade
     * deixam de valer quando a transação confirmar (se ela for desfeita, o link continua valendo).
     */
    public Optional<UUID> consumir(String token, FinalidadeToken finalidade) {
        if (token == null || token.isBlank()) return Optional.empty();
        Optional<UUID> pessoaId = repository.findByTokenHashAndFinalidade(hash(token.trim()), finalidade)
                .filter(registro -> !registro.expirado(Instant.now()))
                .map(TokenEmail::getPessoaId);
        pessoaId.ifPresent(id -> repository.apagar(id, finalidade));
        return pessoaId;
    }

    static String hash(String token) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
