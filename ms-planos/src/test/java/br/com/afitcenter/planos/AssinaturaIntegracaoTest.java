package br.com.afitcenter.planos;

import br.com.afitcenter.core.mensageria.evento.CobrancaEvento;
import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.planos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.CriarCobrancaRequest;
import br.com.afitcenter.planos.model.dto.Dtos.Pagador;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssinaturaIntegracaoTest extends IntegracaoBase {

    @Test
    void primeiroAcessoCriaAssinaturaNoPlanoGratuitoUmaVezSo() throws Exception {
        getComo(ana, "/v1/assinatura")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plano.codigo").value("GRATUITO"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.periodoFim").doesNotExist())
                .andExpect(jsonPath("$.limites.pacientes").value(10))
                .andExpect(jsonPath("$.limites.consultorios").value(1));
        getComo(ana, "/v1/assinatura").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM assinatura WHERE nutricionista_id = ?", Long.class, ana))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT routing_key FROM outbox_evento WHERE payload->>'nutricionistaId' = ? "
                + "AND routing_key LIKE 'assinatura.%'", String.class, ana.toString()))
                .containsExactly("assinatura.criada");
    }

    @Test
    void rotaInternaDevolveOsLimitesECriaSeNaoExistir() throws Exception {
        mvc.perform(get("/internal/assinaturas/" + bruno).header("X-Internal-Token", ChavesTeste.TOKEN_INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutricionistaId").value(bruno.toString()))
                .andExpect(jsonPath("$.planoCodigo").value("GRATUITO"))
                .andExpect(jsonPath("$.limitePacientes").value(10))
                .andExpect(jsonPath("$.versao").value(0));
        mvc.perform(get("/internal/assinaturas/" + bruno)).andExpect(status().isUnauthorized());
    }

    @Test
    void mudancaParaPlanoPagoEmiteFaturaECobrancaSemMudarOPlanoAinda() throws Exception {
        var resposta = postComo(ana, "/v1/assinatura/mudanca-plano",
                "{\"planoId\":%d,\"ciclo\":\"MENSAL\",\"formaPagamento\":\"PIX\"}".formatted(planoId("PROFISSIONAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assinatura.plano.codigo").value("GRATUITO"))
                .andExpect(jsonPath("$.fatura.tipo").value("MUDANCA_PLANO"))
                .andExpect(jsonPath("$.fatura.status").value("ABERTA"))
                .andExpect(jsonPath("$.fatura.valor").value(89.90))
                .andExpect(jsonPath("$.cobranca.pixCopiaECola").value("00020126SIMULADO"));
        UUID fatura = UUID.fromString(corpo(resposta).at("/fatura/id").asText());

        ArgumentCaptor<CriarCobrancaRequest> pedido = ArgumentCaptor.forClass(CriarCobrancaRequest.class);
        verify(cobrancaClient).criar(pedido.capture());
        assertThat(pedido.getValue().origem()).isEqualTo("ASSINATURA");
        assertThat(pedido.getValue().referenciaId()).isEqualTo(fatura);
        assertThat(pedido.getValue().nutricionistaId()).isEqualTo(ana);
        assertThat(pedido.getValue().valor()).isEqualByComparingTo("89.90");
        assertThat(pedido.getValue().chaveIdempotencia()).isEqualTo("fatura:" + fatura + ":1");
        assertThat(pedido.getValue().pagador()).as("pagador buscado no ms-pessoa")
                .isEqualTo(new Pagador("Nutri Teste", "52998224725", "nutri@teste.local"));
        verify(pessoaClient).buscar(ana);
        assertThat(cobrancaDa(fatura)).isNotNull();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE routing_key = 'auditoria.registro'
                  AND payload->>'rota' = '/v1/assinatura/mudanca-plano' AND payload->>'nutricionistaId' = ?""",
                Long.class, ana.toString())).as("trilha de auditoria").isEqualTo(1);
    }

    @Test
    void pagamentoConfirmadoAtivaOPlanoEPublicaOsNovosLimites() throws Exception {
        Instant pagoEm = Instant.parse("2026-10-18T13:00:00Z");
        UUID fatura = pedirMudanca(ana, "PROFISSIONAL", "MENSAL");

        eventoCobranca(fatura, "PAGA", pagoEm);

        getComo(ana, "/v1/assinatura")
                .andExpect(jsonPath("$.plano.codigo").value("PROFISSIONAL"))
                .andExpect(jsonPath("$.ciclo").value("MENSAL"))
                .andExpect(jsonPath("$.periodoInicio").value("2026-10-18T13:00:00Z"))
                .andExpect(jsonPath("$.periodoFim").value("2026-11-18T13:00:00Z"))
                .andExpect(jsonPath("$.limites.pacientes").value(300));
        getComo(ana, "/v1/assinatura/faturas/" + fatura)
                .andExpect(jsonPath("$.status").value("PAGA"))
                .andExpect(jsonPath("$.periodoFim").value("2026-11-18T13:00:00Z"));

        var evento = ultimoEvento(ana);
        assertThat(evento.get("planoCodigo").asText()).isEqualTo("PROFISSIONAL");
        assertThat(evento.get("limitePacientes").asInt()).isEqualTo(300);

        // Reentrega do mesmo pagamento: nada muda.
        eventoCobranca(fatura, "PAGA", pagoEm.plus(1, ChronoUnit.DAYS));
        assertThat(ultimoEvento(ana).get("versao").asLong()).isEqualTo(evento.get("versao").asLong());
        assertThat(periodoFim(ana)).isEqualTo(Instant.parse("2026-11-18T13:00:00Z"));
    }

    @Test
    void novaEscolhaSubstituiAMudancaAindaNaoPaga() throws Exception {
        UUID primeira = pedirMudanca(ana, "PROFISSIONAL", "MENSAL");
        UUID cobrancaPrimeira = cobrancaDa(primeira);

        UUID segunda = pedirMudanca(ana, "CLINICA", "ANUAL");

        verify(cobrancaClient).cancelar(cobrancaPrimeira);
        getComo(ana, "/v1/assinatura/faturas/" + primeira).andExpect(jsonPath("$.status").value("CANCELADA"));
        getComo(ana, "/v1/assinatura/faturas/" + segunda)
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.valor").value(1799.00));
        getComo(ana, "/v1/assinatura/faturas").andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void cobrancaVencidaDesfazAMudancaDePlano() throws Exception {
        UUID fatura = pedirMudanca(ana, "PROFISSIONAL", "MENSAL");
        eventoCobranca(fatura, "VENCIDA", null);

        getComo(ana, "/v1/assinatura/faturas/" + fatura).andExpect(jsonPath("$.status").value("CANCELADA"));
        getComo(ana, "/v1/assinatura").andExpect(jsonPath("$.plano.codigo").value("GRATUITO"));
    }

    @Test
    void msPagamentosForaDoArResponde502ECancelaAFatura() throws Exception {
        doThrow(new FeignException.ServiceUnavailable("fora do ar",
                Request.create(Request.HttpMethod.POST, "/internal/cobrancas", Map.of(), null, StandardCharsets.UTF_8,
                        null), null, Map.of())).when(cobrancaClient).criar(any());

        postComo(ana, "/v1/assinatura/mudanca-plano",
                "{\"planoId\":%d,\"ciclo\":\"MENSAL\",\"formaPagamento\":\"PIX\"}".formatted(planoId("PROFISSIONAL")))
                .andExpect(status().isBadGateway());
        assertThat(jdbc.queryForList("SELECT status FROM fatura WHERE nutricionista_id = ?", String.class, ana))
                .containsExactly("CANCELADA");
    }

    @Test
    void validacoesDaMudanca() throws Exception {
        int profissional = planoId("PROFISSIONAL");
        postComo(ana, "/v1/assinatura/mudanca-plano", "{\"planoId\":%d,\"formaPagamento\":\"PIX\"}".formatted(profissional))
                .andExpect(status().isUnprocessableEntity());
        postComo(ana, "/v1/assinatura/mudanca-plano", "{\"planoId\":%d,\"ciclo\":\"MENSAL\"}".formatted(profissional))
                .andExpect(status().isUnprocessableEntity());
        postComo(ana, "/v1/assinatura/mudanca-plano", "{\"planoId\":%d}".formatted(planoId("GRATUITO")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Você já está neste plano."));
        postComo(ana, "/v1/assinatura/mudanca-plano", "{\"planoId\":999999}").andExpect(status().isNotFound());

        jdbc.update("""
                INSERT INTO plano (codigo, nome, preco_mensal, limite_pacientes, ordem)
                VALUES ('SO_MENSAL_' || upper(substr(md5(random()::text), 1, 6)), 'Só mensal', 19.90, 30, 50)""");
        int soMensal = jdbc.queryForObject("SELECT max(id) FROM plano WHERE codigo LIKE 'SO_MENSAL_%'", Integer.class);
        postComo(ana, "/v1/assinatura/mudanca-plano",
                "{\"planoId\":%d,\"ciclo\":\"ANUAL\",\"formaPagamento\":\"PIX\"}".formatted(soMensal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Este plano não é vendido no ciclo anual."));

        assinarPago(ana, "PROFISSIONAL", Instant.now());
        postComo(ana, "/v1/assinatura/mudanca-plano",
                "{\"planoId\":%d,\"ciclo\":\"MENSAL\",\"formaPagamento\":\"PIX\"}".formatted(profissional))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void faturasIsoladasPorNutricionista() throws Exception {
        UUID fatura = pedirMudanca(ana, "PROFISSIONAL", "MENSAL");

        getComo(bruno, "/v1/assinatura/faturas/" + fatura).andExpect(status().isNotFound());
        postComo(bruno, "/v1/assinatura/faturas/" + fatura + "/cobrancas", "{\"formaPagamento\":\"BOLETO\"}")
                .andExpect(status().isNotFound());
        deleteComo(bruno, "/v1/assinatura/faturas/" + fatura).andExpect(status().isNotFound());
        getComo(bruno, "/v1/assinatura/faturas").andExpect(jsonPath("$.totalElementos").value(0));
        verify(cobrancaClient, never()).cancelar(any());
    }

    @Test
    void novaCobrancaCancelaAAnteriorEUsaOutraChave() throws Exception {
        UUID fatura = pedirMudanca(ana, "PROFISSIONAL", "MENSAL");
        UUID anterior = cobrancaDa(fatura);

        postComo(ana, "/v1/assinatura/faturas/" + fatura + "/cobrancas", "{\"formaPagamento\":\"BOLETO\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formaPagamento").value("BOLETO"));

        verify(cobrancaClient).cancelar(anterior);
        ArgumentCaptor<CriarCobrancaRequest> pedidos = ArgumentCaptor.forClass(CriarCobrancaRequest.class);
        verify(cobrancaClient, org.mockito.Mockito.times(2)).criar(pedidos.capture());
        assertThat(pedidos.getAllValues().get(1).chaveIdempotencia()).isEqualTo("fatura:" + fatura + ":2");
        assertThat(cobrancaDa(fatura)).isNotEqualTo(anterior);

        deleteComo(ana, "/v1/assinatura/faturas/" + fatura).andExpect(status().isNoContent());
        getComo(ana, "/v1/assinatura/faturas/" + fatura).andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    @Test
    void pagamentoDeCobrancaAnteriorQuitaAFaturaECancelaAVigente() throws Exception {
        UUID fatura = pedirMudanca(ana, "PROFISSIONAL", "MENSAL");
        UUID antiga = cobrancaDa(fatura);
        postComo(ana, "/v1/assinatura/faturas/" + fatura + "/cobrancas", "{\"formaPagamento\":\"BOLETO\"}")
                .andExpect(status().isCreated());
        UUID vigente = cobrancaDa(fatura);

        // O Pix antigo foi pago no último instante, antes do cancelamento chegar ao provedor.
        pagamentos.receber(new br.com.afitcenter.core.mensageria.evento.CobrancaEvento(UUID.randomUUID(), Instant.now(),
                antiga, ana, "ASSINATURA", fatura, "PAGA", new BigDecimal("89.90"), Instant.now(), 3));

        getComo(ana, "/v1/assinatura/faturas/" + fatura)
                .andExpect(jsonPath("$.status").value("PAGA"))
                .andExpect(jsonPath("$.cobrancaId").value(antiga.toString()));
        getComo(ana, "/v1/assinatura").andExpect(jsonPath("$.plano.codigo").value("PROFISSIONAL"));
        verify(cobrancaClient).cancelar(vigente);
    }

    @Test
    void segundoPagamentoDaMesmaFaturaVaiParaATrilhaSemMudarAAssinatura() throws Exception {
        UUID fatura = pedirMudanca(ana, "PROFISSIONAL", "MENSAL");
        UUID pix = cobrancaDa(fatura);
        postComo(ana, "/v1/assinatura/faturas/" + fatura + "/cobrancas", "{\"formaPagamento\":\"BOLETO\"}")
                .andExpect(status().isCreated());
        UUID boleto = cobrancaDa(fatura);
        Instant pagoEm = Instant.parse("2026-10-18T13:00:00Z");
        eventoCobranca(fatura, "PAGA", pagoEm);
        long versao = ultimoEvento(ana).get("versao").asLong();

        // O Pix substituído também foi pago, antes de o cancelamento chegar ao provedor.
        pagamentos.receber(new CobrancaEvento(UUID.randomUUID(), Instant.now(), pix, ana, "ASSINATURA", fatura, "PAGA",
                new BigDecimal("89.90"), pagoEm.plusSeconds(60), 3));
        // Reentrega do pagamento do boleto: não é duplicidade.
        eventoCobranca(fatura, "PAGA", pagoEm);

        assertThat(periodoFim(ana)).isEqualTo(Instant.parse("2026-11-18T13:00:00Z"));
        assertThat(ultimoEvento(ana).get("versao").asLong()).isEqualTo(versao);
        assertThat(registrosDeAuditoria("PAGAMENTO_DUPLICADO", fatura)).singleElement().satisfies(detalhes -> {
            assertThat(detalhes.get("cobrancaId").asText()).isEqualTo(pix.toString());
            assertThat(detalhes.get("cobrancaDaFatura").asText()).isEqualTo(boleto.toString());
        });
    }

    @Test
    void novaCobrancaNaoPassaPorCimaDeOutraGeradaAoMesmoTempo() throws Exception {
        UUID fatura = pedirMudanca(ana, "PROFISSIONAL", "MENSAL");
        UUID atual = cobrancaDa(fatura);
        // Enquanto este pedido cancela a cobrança atual, outro pedido abre uma nova tentativa da mesma fatura.
        doAnswer(inv -> jdbc.update("UPDATE fatura SET tentativas_cobranca = tentativas_cobranca + 1, cobranca_id = NULL "
                + "WHERE id = ?", fatura)).when(cobrancaClient).cancelar(atual);

        postComo(ana, "/v1/assinatura/faturas/" + fatura + "/cobrancas", "{\"formaPagamento\":\"BOLETO\"}")
                .andExpect(status().isConflict());
        verify(cobrancaClient, times(1)).criar(any()); // só a da mudança de plano
    }

    @Test
    void cobrancaCriadaDepoisQueAFaturaMudouECancelada() throws Exception {
        UUID fatura = pedirMudanca(ana, "PROFISSIONAL", "MENSAL");
        AtomicReference<UUID> orfa = new AtomicReference<>();
        // Enquanto o ms-pagamentos cria a cobrança, outro pedido abre mais uma tentativa da mesma fatura.
        doAnswer(inv -> {
            jdbc.update("UPDATE fatura SET tentativas_cobranca = tentativas_cobranca + 1 WHERE id = ?", fatura);
            CobrancaResponse criada = cobranca(inv.getArgument(0));
            orfa.set(criada.id());
            return criada;
        }).when(cobrancaClient).criar(any());

        postComo(ana, "/v1/assinatura/faturas/" + fatura + "/cobrancas", "{\"formaPagamento\":\"BOLETO\"}")
                .andExpect(status().isConflict());
        verify(cobrancaClient).cancelar(orfa.get());
        assertThat(cobrancaDa(fatura)).isNull();
    }

    @Test
    void cancelamentoEmDiaValeNoFimDoPeriodoEPodeSerDesfeito() throws Exception {
        assinarPago(ana, "PROFISSIONAL", Instant.parse("2026-10-18T10:00:00Z"));

        postComo(ana, "/v1/assinatura/cancelamento", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plano.codigo").value("PROFISSIONAL"))
                .andExpect(jsonPath("$.planoAgendado.codigo").value("GRATUITO"));
        postComo(ana, "/v1/assinatura/cancelamento", "").andExpect(status().isUnprocessableEntity());

        deleteComo(ana, "/v1/assinatura/cancelamento").andExpect(jsonPath("$.planoAgendado").doesNotExist());
        postComo(ana, "/v1/assinatura/cancelamento", "").andExpect(status().isOk());

        // Ainda no período pago: nada muda.
        ciclo.processarVencimentos(Instant.parse("2026-11-18T09:00:00Z"));
        assertThat(statusAssinatura(ana)).isEqualTo("ATIVA");
        getComo(ana, "/v1/assinatura").andExpect(jsonPath("$.plano.codigo").value("PROFISSIONAL"));

        ciclo.processarVencimentos(Instant.parse("2026-11-18T11:00:00Z"));
        getComo(ana, "/v1/assinatura")
                .andExpect(jsonPath("$.plano.codigo").value("GRATUITO"))
                .andExpect(jsonPath("$.periodoFim").doesNotExist())
                .andExpect(jsonPath("$.limites.pacientes").value(10));
        assertThat(ultimoEvento(ana).get("planoCodigo").asText()).isEqualTo("GRATUITO");
    }

    @Test
    void estornoDaMudancaDePlanoVoltaAoGratuito() throws Exception {
        UUID fatura = assinarPago(ana, "PROFISSIONAL", Instant.now());
        eventoCobranca(fatura, "ESTORNADA", null);

        getComo(ana, "/v1/assinatura/faturas/" + fatura).andExpect(jsonPath("$.status").value("ESTORNADA"));
        getComo(ana, "/v1/assinatura").andExpect(jsonPath("$.plano.codigo").value("GRATUITO"));
    }

    @Test
    void eventoDeOutraOrigemEIgnorado() {
        pagamentos.receber(new br.com.afitcenter.core.mensageria.evento.CobrancaEvento(UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), ana, "OUTRO_PRODUTO", UUID.randomUUID(), "PAGA", BigDecimal.TEN, Instant.now(), 1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assinatura WHERE nutricionista_id = ?", Long.class, ana))
                .isZero();
    }
}
