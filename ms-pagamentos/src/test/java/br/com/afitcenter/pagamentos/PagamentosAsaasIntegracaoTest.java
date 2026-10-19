package br.com.afitcenter.pagamentos;

import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import br.com.afitcenter.pagamentos.provedor.asaas.ProvedorAsaas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O ms-pagamentos inteiro com o Asaas como provedor ativo, falando com o {@link AsaasFalso} (servidor HTTP local):
 * cria, confirma pelo webhook, cancela e estorna, sem chamar a internet.
 */
@TesteIntegracao
@TestPropertySource(properties = {
        "afitcenter.pagamentos.provedor=ASAAS",
        "afitcenter.pagamentos.asaas.habilitado=true",
        "afitcenter.pagamentos.asaas.chave-api=" + PagamentosAsaasIntegracaoTest.CHAVE,
        "afitcenter.pagamentos.asaas.token-webhook=" + PagamentosAsaasIntegracaoTest.TOKEN,
        "afitcenter.pagamentos.simulador.segredo-webhook=segredo-de-teste-com-32-caracteres!",
        "afitcenter.pagamentos.agendador.habilitado=false"
})
class PagamentosAsaasIntegracaoTest {

    static final String CHAVE = "chave-de-teste-do-asaas";
    static final String TOKEN = "token-do-webhook-de-teste-com-32-caracteres";

    static final AsaasFalso asaas = AsaasFalso.iniciar(CHAVE);

    @DynamicPropertySource
    static void urlDoAsaas(DynamicPropertyRegistry registro) {
        registro.add("afitcenter.pagamentos.asaas.url", asaas::url);
    }

    @AfterAll
    static void pararAsaas() {
        asaas.close();
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    private final UUID ana = UUID.randomUUID();
    private final String admin = TokensTeste.comPapeis(UUID.randomUUID(), Papel.ADMIN);

    @Test
    void criaNoAsaasConfirmaPeloWebhookEIgnoraReentregas() throws Exception {
        UUID referencia = UUID.randomUUID();
        String chave = "fatura:" + referencia + ":1";
        JsonNode criada = corpo(criar(chave, referencia, "PIX")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedor").value("ASAAS"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.pixCopiaECola").value(startsWith("00020126ASAASpay_")))
                .andExpect(jsonPath("$.urlPagamento").value(startsWith("https://sandbox.asaas.com/i/pay_"))));
        UUID id = UUID.fromString(criada.get("id").asText());
        String idExterno = idExterno(id);
        assertThat(asaas.pagamento(idExterno).get("externalReference").asText()).isEqualTo(id.toString());
        assertThat(asaas.pagamento(idExterno).get("value").decimalValue()).isEqualByComparingTo("89.90");

        long criadas = asaas.contar("POST /payments");
        criar(chave, referencia, "PIX").andExpect(jsonPath("$.id").value(id.toString()));
        assertThat(asaas.contar("POST /payments")).as("mesma chave não chama o Asaas de novo").isEqualTo(criadas);

        String recebido = evento("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idExterno);
        webhook(recebido, "token-errado").andExpect(status().isUnauthorized());
        webhook(recebido, null).andExpect(status().isUnauthorized());
        assertThat(statusDa(id)).isEqualTo("PENDENTE");

        webhook(recebido, TOKEN).andExpect(status().isOk());
        webhook(recebido, TOKEN).andExpect(status().isOk());
        assertThat(statusDa(id)).isEqualTo("PAGA");
        assertThat(contarEventos(id, "cobranca.paga")).as("reentrega não publica de novo").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacao_provedor WHERE provedor = 'ASAAS' AND id_externo = ?",
                Long.class, idExterno)).isEqualTo(1);
    }

    @Test
    void oClienteDoAsaasEReaproveitadoEntreAsCobrancasDoNutricionista() throws Exception {
        UUID nutri = UUID.randomUUID();
        long antes = asaas.contar("POST /customers");

        criar("fatura:" + UUID.randomUUID() + ":1", nutri, UUID.randomUUID(), "BOLETO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boletoLinhaDigitavel").value("00190000090281913600966281313172593870000008990"));
        criar("fatura:" + UUID.randomUUID() + ":1", nutri, UUID.randomUUID(), "CARTAO_CREDITO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pixCopiaECola").doesNotExist())
                .andExpect(jsonPath("$.urlPagamento").value(startsWith("https://sandbox.asaas.com/i/pay_")));

        assertThat(asaas.contar("POST /customers") - antes).isEqualTo(1);
    }

    @Test
    void falhaDepoisDeCriarNoAsaasNaoDuplicaACobranca() throws Exception {
        UUID referencia = UUID.randomUUID();
        String chave = "fatura:" + referencia + ":1";
        long antes = asaas.contar("POST /payments");
        asaas.falhasPix.set(1);

        criar(chave, referencia, "PIX").andExpect(status().isBadGateway());
        assertThat(jdbc.queryForObject("SELECT id_externo FROM cobranca WHERE chave_idempotencia = ?", String.class, chave))
                .isNull();

        criar(chave, referencia, "PIX").andExpect(status().isOk())
                .andExpect(jsonPath("$.pixCopiaECola").value(startsWith("00020126ASAAS")));
        assertThat(asaas.contar("POST /payments") - antes).as("achada pela externalReference").isEqualTo(1);
    }

    @Test
    void cartaoConfirmadoEDepoisRecebidoContaUmPagamento() throws Exception {
        UUID id = novaCobranca("CARTAO_CREDITO");
        String idExterno = idExterno(id);

        webhook(evento("evt_" + UUID.randomUUID(), "PAYMENT_CHECKOUT_VIEWED", idExterno), TOKEN).andExpect(status().isOk());
        assertThat(statusDa(id)).isEqualTo("PENDENTE");
        webhook(evento("evt_" + UUID.randomUUID(), "PAYMENT_CONFIRMED", idExterno), TOKEN).andExpect(status().isOk());
        webhook(evento("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idExterno), TOKEN).andExpect(status().isOk());

        assertThat(statusDa(id)).isEqualTo("PAGA");
        assertThat(contarEventos(id, "cobranca.paga")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT resultado FROM notificacao_provedor WHERE id_externo = ? ORDER BY recebida_em",
                String.class, idExterno)).containsExactly("ignorada", "PENDENTE -> PAGA", "ignorada: cobrança PAGA");
    }

    @Test
    void cancelamentoRemoveNoAsaasMasNaoCancelaCobrancaPaga() throws Exception {
        UUID boleto = novaCobranca("BOLETO");
        cancelar(boleto).andExpect(status().isNoContent());
        assertThat(statusDa(boleto)).isEqualTo("CANCELADA");
        assertThat(asaas.pagamento(idExterno(boleto)).get("deleted").asBoolean()).isTrue();
        cancelar(boleto).andExpect(status().isNoContent());

        UUID pix = novaCobranca("PIX");
        asaas.pagar(idExterno(pix));
        cancelar(pix).andExpect(status().isConflict());
        assertThat(statusDa(pix)).as("o webhook do pagamento está a caminho").isEqualTo("PENDENTE");
        assertThat(asaas.pagamento(idExterno(pix)).get("deleted").asBoolean()).isFalse();
    }

    @Test
    void estornoDePixPelaApiEDeBoletoPeloPainel() throws Exception {
        UUID pix = novaCobranca("PIX");
        pagarPeloWebhook(pix);
        estornar(pix).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ESTORNADA"));
        assertThat(asaas.pagamento(idExterno(pix)).get("status").asText()).isEqualTo("REFUND_REQUESTED");
        webhook(evento("evt_" + UUID.randomUUID(), "PAYMENT_REFUNDED", idExterno(pix)), TOKEN).andExpect(status().isOk());
        assertThat(contarEventos(pix, "cobranca.estornada")).isEqualTo(1);

        UUID boleto = novaCobranca("BOLETO");
        pagarPeloWebhook(boleto);
        estornar(boleto).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(startsWith("Boletos pagos no Asaas são estornados pelo painel")));
        webhook(evento("evt_" + UUID.randomUUID(), "PAYMENT_REFUNDED", idExterno(boleto)), TOKEN).andExpect(status().isOk());
        assertThat(statusDa(boleto)).as("estorno feito no painel chega pelo webhook").isEqualTo("ESTORNADA");
    }

    // ------------------------------------------------------------------ apoio

    private ResultActions criar(String chave, UUID referencia, String forma) throws Exception {
        return criar(chave, ana, referencia, forma);
    }

    private ResultActions criar(String chave, UUID nutricionista, UUID referencia, String forma) throws Exception {
        ObjectNode corpo = json.createObjectNode()
                .put("chaveIdempotencia", chave)
                .put("nutricionistaId", nutricionista.toString())
                .put("origem", "ASSINATURA")
                .put("referenciaId", referencia.toString())
                .put("descricao", "AFitCenter - assinatura do plano Profissional (mensal)")
                .put("valor", new BigDecimal("89.90"))
                .put("vencimento", LocalDate.now().plusDays(3).toString())
                .put("formaPagamento", forma);
        corpo.putObject("pagador").put("nome", "Ana Nutri").put("cpfCnpj", "52998224725").put("email", "ana@teste.local");
        return mvc.perform(post("/internal/cobrancas").header("X-Internal-Token", ChavesTeste.TOKEN_INTERNO)
                .contentType(MediaType.APPLICATION_JSON).content(corpo.toString()));
    }

    private UUID novaCobranca(String forma) throws Exception {
        UUID referencia = UUID.randomUUID();
        return UUID.fromString(corpo(criar("fatura:" + referencia + ":1", referencia, forma)
                .andExpect(status().isOk())).get("id").asText());
    }

    private void pagarPeloWebhook(UUID id) throws Exception {
        asaas.pagar(idExterno(id));
        webhook(evento("evt_" + UUID.randomUUID(), "PAYMENT_RECEIVED", idExterno(id)), TOKEN).andExpect(status().isOk());
        assertThat(statusDa(id)).isEqualTo("PAGA");
    }

    private ResultActions cancelar(UUID id) throws Exception {
        return mvc.perform(post("/internal/cobrancas/" + id + "/cancelamento")
                .header("X-Internal-Token", ChavesTeste.TOKEN_INTERNO));
    }

    private ResultActions estornar(UUID id) throws Exception {
        return mvc.perform(post("/v1/admin/cobrancas/" + id + "/estorno").header("Authorization", admin));
    }

    /** Notificação no formato do Asaas. */
    private String evento(String idEvento, String evento, String idExterno) {
        ObjectNode corpo = json.createObjectNode().put("id", idEvento).put("event", evento)
                .put("dateCreated", "2026-10-18 12:30:00");
        corpo.putObject("payment").put("object", "payment").put("id", idExterno).put("value", new BigDecimal("89.90"))
                .put("netValue", new BigDecimal("88.91")).put("status", "RECEIVED");
        return corpo.toString();
    }

    private ResultActions webhook(String corpo, String token) throws Exception {
        var requisicao = post("/v1/webhooks/asaas").contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (token != null) requisicao.header(ProvedorAsaas.HEADER_TOKEN, token);
        return mvc.perform(requisicao);
    }

    private JsonNode corpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private String idExterno(UUID id) {
        return jdbc.queryForObject("SELECT id_externo FROM cobranca WHERE id = ?", String.class, id);
    }

    private String statusDa(UUID id) {
        return jdbc.queryForObject("SELECT status FROM cobranca WHERE id = ?", String.class, id);
    }

    private long contarEventos(UUID id, String routingKey) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE agregado_id = ? AND routing_key = ?",
                Long.class, id, routingKey);
    }
}
