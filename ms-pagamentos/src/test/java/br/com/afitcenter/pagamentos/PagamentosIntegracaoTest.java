package br.com.afitcenter.pagamentos;

import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import br.com.afitcenter.pagamentos.provedor.NotificacaoPagamento.Tipo;
import br.com.afitcenter.pagamentos.provedor.ProvedorSimulado;
import br.com.afitcenter.pagamentos.service.CobrancaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteIntegracao
@TestPropertySource(properties = {
        "afitcenter.pagamentos.simulador.segredo-webhook=segredo-de-teste-com-32-caracteres!",
        "afitcenter.pagamentos.simulador.endpoints-habilitados=true",
        "afitcenter.pagamentos.agendador.habilitado=false"
})
class PagamentosIntegracaoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CobrancaService cobrancas;

    @MockitoSpyBean
    ProvedorSimulado simulado;

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();

    @Test
    void criacaoInternaIdempotentePelaChave() throws Exception {
        UUID referencia = UUID.randomUUID();
        String chave = "fatura:" + referencia + ":1";
        JsonNode criada = corpo(criar(chave, ana, referencia, "PIX", LocalDate.now().plusDays(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.provedor").value("SIMULADO"))
                .andExpect(jsonPath("$.pixCopiaECola").value(org.hamcrest.Matchers.startsWith("000201"))));

        criar(chave, ana, referencia, "PIX", LocalDate.now().plusDays(3))
                .andExpect(jsonPath("$.id").value(criada.get("id").asText()));
        criar(chave, ana, referencia, "BOLETO", LocalDate.now().plusDays(3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A chave de idempotência já foi usada em outra cobrança."));
        criar("fatura:" + referencia + ":2", ana, referencia, "BOLETO", LocalDate.now().plusDays(3))
                .andExpect(status().isConflict());

        mvc.perform(post("/internal/cobrancas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        ObjectNode semPagador = json.createObjectNode().put("chaveIdempotencia", "fatura:" + UUID.randomUUID() + ":1")
                .put("nutricionistaId", ana.toString()).put("origem", "ASSINATURA")
                .put("referenciaId", UUID.randomUUID().toString()).put("descricao", "Assinatura")
                .put("valor", 89.90).put("vencimento", LocalDate.now().plusDays(3).toString()).put("formaPagamento", "PIX");
        criar(semPagador).andExpect(status().isBadRequest());
        semPagador.putObject("pagador").put("nome", "Ana").put("cpfCnpj", "529.982.247-25").put("email", "ana@teste.local");
        criar(semPagador).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['pagador.cpfCnpj']").value("Informe o CPF ou CNPJ só com números."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE routing_key = 'auditoria.registro' "
                + "AND payload->>'recursoId' = ? AND payload->>'acao' = 'CRIACAO'", Long.class, criada.get("id").asText()))
                .as("criação registrada na trilha").isEqualTo(1);
    }

    @Test
    void falhaNoProvedorResponde502ERepetirRetomaACriacao() throws Exception {
        doThrow(new IllegalStateException("timeout")).doCallRealMethod().when(simulado).criar(any());
        UUID referencia = UUID.randomUUID();
        String chave = "fatura:" + referencia + ":1";

        criar(chave, ana, referencia, "BOLETO", LocalDate.now().plusDays(3)).andExpect(status().isBadGateway());
        assertThat(jdbc.queryForObject("SELECT id_externo FROM cobranca WHERE chave_idempotencia = ?", String.class, chave))
                .isNull();

        criar(chave, ana, referencia, "BOLETO", LocalDate.now().plusDays(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boletoLinhaDigitavel").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cobranca WHERE referencia_id = ?", Long.class, referencia))
                .isEqualTo(1);
    }

    @Test
    void nutricionistaSoVeAsPropriasCobrancas() throws Exception {
        UUID id = novaCobranca(ana, "CARTAO_CREDITO");

        getComo(ana, "/v1/cobrancas/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPagamento").value(org.hamcrest.Matchers.containsString("/sim_")));
        getComo(bruno, "/v1/cobrancas/" + id).andExpect(status().isNotFound());
        getComo(bruno, "/v1/cobrancas").andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(post("/v1/simulador/cobrancas/" + id + "/pagamento")
                        .header("Authorization", TokensTeste.nutricionista(bruno)))
                .andExpect(status().isNotFound());
    }

    @Test
    void simuladorPagaPeloMesmoCaminhoDoWebhookEPublicaOEvento() throws Exception {
        UUID id = novaCobranca(ana, "PIX");

        mvc.perform(post("/v1/simulador/cobrancas/" + id + "/pagamento")
                        .header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"))
                .andExpect(jsonPath("$.valorPago").value(89.90));

        JsonNode evento = eventos(id, "cobranca.paga");
        assertThat(evento.get("status").asText()).isEqualTo("PAGA");
        assertThat(evento.get("origem").asText()).isEqualTo("ASSINATURA");
        assertThat(evento.get("nutricionistaId").asText()).isEqualTo(ana.toString());
        assertThat(evento.get("pagoEm").isNull()).isFalse();
    }

    @Test
    void webhookExigeAssinaturaEIgnoraReentregas() throws Exception {
        UUID id = novaCobranca(ana, "PIX");
        String idExterno = jdbc.queryForObject("SELECT id_externo FROM cobranca WHERE id = ?", String.class, id);
        String corpo = simulado.notificacao(idExterno, Tipo.PAGAMENTO_CONFIRMADO, new BigDecimal("89.90"));

        webhook("simulado", corpo, "assinatura-falsa").andExpect(status().isUnauthorized());
        webhook("simulado", corpo, null).andExpect(status().isUnauthorized());
        webhook("outro", corpo, simulado.assinar(corpo)).andExpect(status().isNotFound());
        assertThat(statusDa(id)).isEqualTo("PENDENTE");

        webhook("simulado", corpo, simulado.assinar(corpo)).andExpect(status().isOk());
        webhook("SIMULADO", corpo, simulado.assinar(corpo)).andExpect(status().isOk());
        assertThat(statusDa(id)).isEqualTo("PAGA");
        assertThat(contarEventos(id, "cobranca.paga")).as("reentrega não publica de novo").isEqualTo(1);

        String desconhecida = simulado.notificacao("sim_inexistente", Tipo.PAGAMENTO_CONFIRMADO, BigDecimal.ONE);
        webhook("simulado", desconhecida, simulado.assinar(desconhecida)).andExpect(status().isOk());
    }

    @Test
    void cancelamentoInternoIdempotenteEBloqueadoDepoisDePaga() throws Exception {
        UUID id = novaCobranca(ana, "BOLETO");

        cancelar(id).andExpect(status().isNoContent());
        cancelar(id).andExpect(status().isNoContent());
        assertThat(statusDa(id)).isEqualTo("CANCELADA");
        assertThat(contarEventos(id, "cobranca.cancelada")).isEqualTo(1);

        UUID paga = novaCobranca(ana, "PIX");
        mvc.perform(post("/v1/simulador/cobrancas/" + paga + "/pagamento")
                .header("Authorization", TokensTeste.nutricionista(ana))).andExpect(status().isOk());
        cancelar(paga).andExpect(status().isConflict());
        cancelar(UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void pendenteVencidaExpiraMasPagamentoAtrasadoAindaEAceito() throws Exception {
        UUID referencia = UUID.randomUUID();
        UUID id = UUID.fromString(corpo(criar("fatura:" + referencia + ":1", ana, referencia, "PIX",
                LocalDate.now().minusDays(3))).get("id").asText());
        UUID emDia = novaCobranca(ana, "PIX");

        cobrancas.expirar();

        assertThat(statusDa(id)).isEqualTo("VENCIDA");
        assertThat(statusDa(emDia)).isEqualTo("PENDENTE");
        assertThat(eventos(id, "cobranca.vencida").get("status").asText()).isEqualTo("VENCIDA");

        String idExterno = jdbc.queryForObject("SELECT id_externo FROM cobranca WHERE id = ?", String.class, id);
        String corpo = simulado.notificacao(idExterno, Tipo.PAGAMENTO_CONFIRMADO, new BigDecimal("89.90"));
        webhook("simulado", corpo, simulado.assinar(corpo)).andExpect(status().isOk());
        assertThat(statusDa(id)).isEqualTo("PAGA");
    }

    @Test
    void cartaoRecusadoEEstornoPeloAdministrador() throws Exception {
        UUID cartao = novaCobranca(ana, "CARTAO_CREDITO");
        mvc.perform(post("/v1/simulador/cobrancas/" + cartao + "/recusa")
                        .header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(jsonPath("$.status").value("RECUSADA"));
        assertThat(contarEventos(cartao, "cobranca.recusada")).isEqualTo(1);

        UUID pix = novaCobranca(ana, "PIX");
        mvc.perform(post("/v1/simulador/cobrancas/" + pix + "/recusa")
                        .header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isUnprocessableEntity());

        String admin = TokensTeste.comPapeis(UUID.randomUUID(), Papel.ADMIN);
        mvc.perform(post("/v1/admin/cobrancas/" + pix + "/estorno").header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/simulador/cobrancas/" + pix + "/pagamento").header("Authorization", admin))
                .andExpect(jsonPath("$.status").value("PAGA"));
        mvc.perform(post("/v1/admin/cobrancas/" + pix + "/estorno").header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/cobrancas/" + pix + "/estorno").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADA"));
        assertThat(contarEventos(pix, "cobranca.estornada")).isEqualTo(1);

        mvc.perform(get("/v1/admin/cobrancas").param("nutricionistaId", ana.toString()).header("Authorization", admin))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    // ------------------------------------------------------------------ apoio

    private ResultActions criar(String chave, UUID nutricionista, UUID referencia, String forma, LocalDate vencimento)
            throws Exception {
        ObjectNode corpo = json.createObjectNode()
                .put("chaveIdempotencia", chave)
                .put("nutricionistaId", nutricionista.toString())
                .put("origem", "ASSINATURA")
                .put("referenciaId", referencia.toString())
                .put("descricao", "AFitCenter - assinatura do plano Profissional (mensal)")
                .put("valor", new BigDecimal("89.90"))
                .put("vencimento", vencimento.toString())
                .put("formaPagamento", forma);
        corpo.putObject("pagador").put("nome", "Ana Nutri").put("cpfCnpj", "52998224725").put("email", "ana@teste.local");
        return criar(corpo);
    }

    private ResultActions criar(ObjectNode corpo) throws Exception {
        return mvc.perform(post("/internal/cobrancas").header("X-Internal-Token", ChavesTeste.TOKEN_INTERNO)
                .contentType(MediaType.APPLICATION_JSON).content(corpo.toString()));
    }

    private UUID novaCobranca(UUID nutricionista, String forma) throws Exception {
        UUID referencia = UUID.randomUUID();
        return UUID.fromString(corpo(criar("fatura:" + referencia + ":1", nutricionista, referencia, forma,
                LocalDate.now().plusDays(3)).andExpect(status().isOk())).get("id").asText());
    }

    private ResultActions cancelar(UUID id) throws Exception {
        return mvc.perform(post("/internal/cobrancas/" + id + "/cancelamento")
                .header("X-Internal-Token", ChavesTeste.TOKEN_INTERNO));
    }

    private ResultActions webhook(String provedor, String corpo, String assinatura) throws Exception {
        var requisicao = post("/v1/webhooks/" + provedor).contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (assinatura != null) requisicao.header(ProvedorSimulado.HEADER_ASSINATURA, assinatura);
        return mvc.perform(requisicao);
    }

    private ResultActions getComo(UUID nutricionista, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", TokensTeste.nutricionista(nutricionista)));
    }

    private JsonNode corpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private String statusDa(UUID id) {
        return jdbc.queryForObject("SELECT status FROM cobranca WHERE id = ?", String.class, id);
    }

    private long contarEventos(UUID id, String routingKey) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE agregado_id = ? AND routing_key = ?",
                Long.class, id, routingKey);
    }

    private JsonNode eventos(UUID id, String routingKey) throws Exception {
        return json.readTree(jdbc.queryForObject("""
                SELECT payload::text FROM outbox_evento WHERE agregado_id = ? AND routing_key = ?
                ORDER BY criado_em DESC LIMIT 1""", String.class, id, routingKey));
    }
}
