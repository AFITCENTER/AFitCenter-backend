package br.com.afitcenter.planos;

import br.com.afitcenter.planos.model.dto.Dtos.CriarCobrancaRequest;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class CicloAssinaturaTest extends IntegracaoBase {

    private static final Instant PAGO_EM = Instant.parse("2026-10-18T12:00:00Z");
    private static final Instant FIM = Instant.parse("2026-11-18T12:00:00Z");

    @Test
    void renovacaoEmitidaUmaVezSoAntesDoFimDoPeriodo() throws Exception {
        assinarPago(ana, "PROFISSIONAL", PAGO_EM);

        ciclo.emitirRenovacoes(FIM.minus(Duration.ofDays(8)));
        assertThat(renovacoes(ana)).isZero();

        ciclo.emitirRenovacoes(FIM.minus(Duration.ofDays(5)));
        ciclo.emitirRenovacoes(FIM.minus(Duration.ofDays(4)));
        assertThat(renovacoes(ana)).isEqualTo(1);

        var fatura = jdbc.queryForMap("SELECT * FROM fatura WHERE nutricionista_id = ? AND tipo = 'RENOVACAO'", ana);
        assertThat(((java.sql.Timestamp) fatura.get("periodo_inicio")).toInstant()).isEqualTo(FIM);
        assertThat(((java.sql.Date) fatura.get("vencimento")).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(fatura.get("cobranca_id")).isNotNull();

        UUID renovacao = renovacao(ana);
        assertThat(pedidosDa(renovacao)).singleElement()
                .satisfies(p -> assertThat(p.descricao()).contains("renovação"));
    }

    @Test
    void renovacaoPagaEstendeOPeriodo() throws Exception {
        assinarPago(ana, "PROFISSIONAL", PAGO_EM);
        ciclo.emitirRenovacoes(FIM.minus(Duration.ofDays(3)));
        UUID renovacao = renovacao(ana);

        eventoCobranca(renovacao, "PAGA", FIM.minus(Duration.ofDays(2)));

        getComo(ana, "/v1/assinatura")
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.periodoInicio").value("2026-10-18T12:00:00Z"))
                .andExpect(jsonPath("$.periodoFim").value("2026-12-18T12:00:00Z"));
        ciclo.processarVencimentos(FIM.plus(Duration.ofHours(1)));
        assertThat(statusAssinatura(ana)).isEqualTo("ATIVA");
    }

    @Test
    void semPagamentoFicaInadimplenteDepoisSuspensaEVoltaAoPagar() throws Exception {
        assinarPago(ana, "PROFISSIONAL", PAGO_EM);
        ciclo.emitirRenovacoes(FIM.minus(Duration.ofDays(3)));
        UUID renovacao = renovacao(ana);

        ciclo.processarVencimentos(FIM.plus(Duration.ofHours(1)));
        assertThat(statusAssinatura(ana)).isEqualTo("INADIMPLENTE");
        assertThat(ultimoEvento(ana).get("limitePacientes").asInt()).as("carência mantém os limites").isEqualTo(300);

        ciclo.processarVencimentos(FIM.plus(Duration.ofDays(6)));
        assertThat(statusAssinatura(ana)).isEqualTo("INADIMPLENTE");

        ciclo.processarVencimentos(FIM.plus(Duration.ofDays(7)).plusSeconds(1));
        assertThat(statusAssinatura(ana)).isEqualTo("SUSPENSA");
        var suspensa = ultimoEvento(ana);
        assertThat(suspensa.get("status").asText()).isEqualTo("SUSPENSA");
        assertThat(suspensa.get("limitePacientes").asInt()).as("limites do plano gratuito").isEqualTo(10);
        getComo(ana, "/v1/assinatura").andExpect(jsonPath("$.limites.consultorios").value(1));

        // Mudar de plano exige regularizar antes.
        postComo(ana, "/v1/assinatura/mudanca-plano",
                "{\"planoId\":%d,\"ciclo\":\"MENSAL\",\"formaPagamento\":\"PIX\"}".formatted(planoId("CLINICA")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isUnprocessableEntity());

        Instant pagoTarde = FIM.plus(Duration.ofDays(10));
        eventoCobranca(renovacao, "PAGA", pagoTarde);
        getComo(ana, "/v1/assinatura")
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.periodoInicio").value("2026-11-28T12:00:00Z"))
                .andExpect(jsonPath("$.periodoFim").value("2026-12-28T12:00:00Z"))
                .andExpect(jsonPath("$.limites.pacientes").value(300));
    }

    @Test
    void cancelarInadimplenteVoltaAoGratuitoNaHoraECancelaARenovacao() throws Exception {
        assinarPago(ana, "PROFISSIONAL", PAGO_EM);
        ciclo.emitirRenovacoes(FIM.minus(Duration.ofDays(3)));
        UUID renovacao = renovacao(ana);
        UUID cobranca = cobrancaDa(renovacao);
        ciclo.processarVencimentos(FIM.plus(Duration.ofHours(1)));

        postComo(ana, "/v1/assinatura/cancelamento", "")
                .andExpect(jsonPath("$.plano.codigo").value("GRATUITO"))
                .andExpect(jsonPath("$.status").value("ATIVA"));
        verify(cobrancaClient).cancelar(cobranca);
        getComo(ana, "/v1/assinatura/faturas/" + renovacao).andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void renovacaoSemCobrancaEReemitidaDepois() throws Exception {
        assinarPago(ana, "PROFISSIONAL", PAGO_EM);
        reset(cobrancaClient);
        when(cobrancaClient.criar(any())).thenThrow(new FeignException.ServiceUnavailable("fora do ar",
                Request.create(Request.HttpMethod.POST, "/internal/cobrancas", Map.of(), null, StandardCharsets.UTF_8,
                        null), null, Map.of()));

        ciclo.emitirRenovacoes(FIM.minus(Duration.ofDays(3)));
        UUID renovacao = renovacao(ana);
        assertThat(cobrancaDa(renovacao)).isNull();

        reset(cobrancaClient);
        when(cobrancaClient.criar(any())).thenAnswer(inv -> cobranca(inv.getArgument(0)));
        // Só faturas paradas há mais de um minuto (a emissão original pode estar em andamento).
        ciclo.reemitirCobrancas(Instant.now());
        assertThat(cobrancaDa(renovacao)).isNull();
        ciclo.reemitirCobrancas(Instant.now().plus(Duration.ofMinutes(2)));

        assertThat(cobrancaDa(renovacao)).isNotNull();
        assertThat(pedidosDa(renovacao)).singleElement()
                .satisfies(p -> assertThat(p.chaveIdempotencia()).isEqualTo("fatura:" + renovacao + ":1"));
    }

    /** O agendador processa todas as assinaturas do banco: filtra os pedidos desta fatura. */
    private java.util.List<CriarCobrancaRequest> pedidosDa(UUID fatura) {
        ArgumentCaptor<CriarCobrancaRequest> pedidos = ArgumentCaptor.forClass(CriarCobrancaRequest.class);
        verify(cobrancaClient, atLeastOnce()).criar(pedidos.capture());
        return pedidos.getAllValues().stream().filter(p -> p.referenciaId().equals(fatura)).toList();
    }

    private long renovacoes(UUID nutricionista) {
        return jdbc.queryForObject("SELECT count(*) FROM fatura WHERE nutricionista_id = ? AND tipo = 'RENOVACAO'",
                Long.class, nutricionista);
    }

    private UUID renovacao(UUID nutricionista) {
        return jdbc.queryForObject("SELECT id FROM fatura WHERE nutricionista_id = ? AND tipo = 'RENOVACAO'",
                UUID.class, nutricionista);
    }
}
