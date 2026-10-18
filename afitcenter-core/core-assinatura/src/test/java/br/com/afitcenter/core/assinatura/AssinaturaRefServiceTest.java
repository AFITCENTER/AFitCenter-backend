package br.com.afitcenter.core.assinatura;

import br.com.afitcenter.core.web.erro.RegraNegocioException;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssinaturaRefServiceTest {

    private final AssinaturaRefRepository repository = mock(AssinaturaRefRepository.class);
    private final AssinaturaInternoClient client = mock(AssinaturaInternoClient.class);
    private final AssinaturaRefService service =
            new AssinaturaRefService(repository, client, TransactionOperations.withoutTransaction());

    private final UUID nutri = UUID.randomUUID();

    @Test
    void abaixoDoLimitePermiteSemChamarOMsPlanos() {
        when(repository.bloquear(nutri)).thenReturn(Optional.of(ref("ATIVA", 10)));

        assertThatCode(() -> service.exigirDisponivel(nutri, Limite.PACIENTES, () -> 9)).doesNotThrowAnyException();
        verify(client, never()).buscar(any());
    }

    @Test
    void limiteAtingidoRecusaComMensagemDoPlano() {
        when(repository.bloquear(nutri)).thenReturn(Optional.of(ref("ATIVA", 10)));

        assertThatThrownBy(() -> service.exigirDisponivel(nutri, Limite.PACIENTES, () -> 10))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("até 10 pacientes");
    }

    @Test
    void assinaturaSuspensaExplicaQueValemOsLimitesDoGratuito() {
        when(repository.bloquear(nutri)).thenReturn(Optional.of(ref("SUSPENSA", 5)));

        assertThatThrownBy(() -> service.exigirDisponivel(nutri, Limite.PACIENTES, () -> 7))
                .hasMessageContaining("suspensa");
    }

    @Test
    void semLimiteNaoContaOUso() {
        when(repository.bloquear(nutri)).thenReturn(Optional.of(ref("ATIVA", null)));
        AtomicBoolean contou = new AtomicBoolean();

        service.exigirDisponivel(nutri, Limite.PACIENTES, () -> {
            contou.set(true);
            return 1_000_000;
        });
        assertThat(contou).isFalse();
    }

    @Test
    void semReplicaBuscaNoMsPlanosEGravaLocalmente() {
        AssinaturaRef remota = ref("ATIVA", 1);
        when(repository.bloquear(nutri)).thenReturn(Optional.empty(), Optional.of(remota));
        when(client.buscar(nutri)).thenReturn(remota);

        assertThatThrownBy(() -> service.exigirDisponivel(nutri, Limite.PACIENTES, () -> 1))
                .isInstanceOf(RegraNegocioException.class);
        verify(repository).aplicar(remota);
    }

    @Test
    void msPlanosForaDoArPermiteACriacao() {
        when(repository.bloquear(nutri)).thenReturn(Optional.empty());
        when(client.buscar(nutri)).thenThrow(new FeignException.ServiceUnavailable("fora do ar",
                Request.create(Request.HttpMethod.GET, "/internal/assinaturas/x", Map.of(), null,
                        StandardCharsets.UTF_8, null), null, Map.of()));

        assertThatCode(() -> service.exigirDisponivel(nutri, Limite.PACIENTES, () -> 1_000))
                .doesNotThrowAnyException();
        verify(repository, never()).aplicar(any());
    }

    private AssinaturaRef ref(String status, Integer limitePacientes) {
        return new AssinaturaRef(nutri, UUID.randomUUID(), "GRATUITO", status, limitePacientes, 1, 3);
    }
}
