package br.com.afitcenter.pagamentos.provedor.asaas;

import br.com.afitcenter.pagamentos.model.enums.Enums.FormaPagamento;
import br.com.afitcenter.pagamentos.provedor.DadosPagamento;
import br.com.afitcenter.pagamentos.provedor.NotificacaoPagamento;
import br.com.afitcenter.pagamentos.provedor.NotificacaoPagamento.Tipo;
import br.com.afitcenter.pagamentos.provedor.NovaCobrancaProvedor;
import br.com.afitcenter.pagamentos.provedor.Pagador;
import br.com.afitcenter.pagamentos.provedor.ProvedorPagamento.CobrancaJaPagaException;
import br.com.afitcenter.pagamentos.provedor.ProvedorPagamento.EstornoIndisponivelException;
import br.com.afitcenter.pagamentos.provedor.ProvedorPagamento.NotificacaoInvalidaException;
import br.com.afitcenter.pagamentos.provedor.asaas.AsaasApi.ErroAsaasException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Cliente HTTP e webhook do Asaas contra um servidor simulado: nenhuma chamada sai para a internet. */
class ProvedorAsaasTest {

    private static final String URL = "https://asaas.teste/v3";
    private static final String CHAVE = "$aact_hmlg_chave-de-teste";
    private static final String TOKEN = "token-do-webhook-de-teste-com-32-caracteres";
    private static final Instant AGORA = Instant.parse("2026-10-18T15:00:00Z");

    private final ObjectMapper json = new ObjectMapper();
    private final UUID nutricionista = UUID.randomUUID();
    private MockRestServiceServer asaas;
    private ProvedorAsaas provedor;

    @BeforeEach
    void preparar() {
        RestClient.Builder http = RestClient.builder();
        asaas = MockRestServiceServer.bindTo(http).build();
        provedor = new ProvedorAsaas(http, json, Clock.fixed(AGORA, ZoneOffset.UTC), URL, CHAVE, TOKEN);
    }

    // ------------------------------------------------------------------ criação

    @Test
    void pixCriaClienteECobrancaComAReferenciaEDevolveOCopiaECola() {
        UUID cobranca = UUID.randomUUID();
        asaas.expect(requestTo(URL + "/payments?externalReference=" + cobranca)).andExpect(method(GET))
                .andExpect(header("access_token", CHAVE))
                .andExpect(header("User-Agent", AsaasApi.USER_AGENT))
                .andRespond(withSuccess(lista(), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/customers?externalReference=" + nutricionista + "&limit=1")).andExpect(method(GET))
                .andRespond(withSuccess(lista(), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/customers")).andExpect(method(POST))
                .andExpect(header("access_token", CHAVE))
                .andExpect(jsonPath("$.name").value("Ana Nutri"))
                .andExpect(jsonPath("$.cpfCnpj").value("52998224725"))
                .andExpect(jsonPath("$.email").value("ana@teste.local"))
                .andExpect(jsonPath("$.externalReference").value(nutricionista.toString()))
                .andExpect(jsonPath("$.notificationDisabled").value(true))
                .andRespond(withSuccess("{\"object\":\"customer\",\"id\":\"cus_1\"}", APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/payments")).andExpect(method(POST))
                .andExpect(jsonPath("$.customer").value("cus_1"))
                .andExpect(jsonPath("$.billingType").value("PIX"))
                .andExpect(jsonPath("$.value").value(89.90))
                .andExpect(jsonPath("$.dueDate").value("2026-10-21"))
                .andExpect(jsonPath("$.description").value("AFitCenter - assinatura"))
                .andExpect(jsonPath("$.externalReference").value(cobranca.toString()))
                .andRespond(withSuccess(pagamento("pay_1", "PENDING", "PIX"), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/payments/pay_1/pixQrCode")).andExpect(method(GET))
                .andRespond(withSuccess("{\"encodedImage\":\"iVBOR\",\"payload\":\"00020126ASAAS\"}", APPLICATION_JSON));

        DadosPagamento dados = provedor.criar(nova(cobranca, FormaPagamento.PIX));

        assertThat(dados).isEqualTo(new DadosPagamento("pay_1", "00020126ASAAS", null,
                "https://sandbox.asaas.com/i/pay_1"));
        asaas.verify();
    }

    @Test
    void repetirDepoisDeTimeoutReaproveitaACobrancaJaCriada() {
        UUID cobranca = UUID.randomUUID();
        asaas.expect(requestTo(URL + "/payments?externalReference=" + cobranca))
                .andRespond(withSuccess(lista(pagamento("pay_velho", "PENDING", "BOLETO", true),
                        pagamento("pay_2", "PENDING", "BOLETO")), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/payments/pay_2/identificationField")).andExpect(method(GET))
                .andRespond(withSuccess("{\"identificationField\":\"00190000090281913600966281313172593870000008990\","
                        + "\"nossoNumero\":\"123\",\"barCode\":\"001\"}", APPLICATION_JSON));

        DadosPagamento dados = provedor.criar(nova(cobranca, FormaPagamento.BOLETO));

        assertThat(dados.idExterno()).as("a removida é ignorada").isEqualTo("pay_2");
        assertThat(dados.boletoLinhaDigitavel()).isEqualTo("00190000090281913600966281313172593870000008990");
        asaas.verify();
    }

    @Test
    void cartaoReaproveitaOClienteEVaiPelaFaturaHospedada() {
        UUID cobranca = UUID.randomUUID();
        asaas.expect(requestTo(URL + "/payments?externalReference=" + cobranca)).andRespond(withSuccess(lista(), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/customers?externalReference=" + nutricionista + "&limit=1"))
                .andRespond(withSuccess(lista("{\"id\":\"cus_9\",\"deleted\":false}"), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/payments")).andExpect(method(POST))
                .andExpect(jsonPath("$.customer").value("cus_9"))
                .andExpect(jsonPath("$.billingType").value("CREDIT_CARD"))
                .andRespond(withSuccess(pagamento("pay_3", "PENDING", "CREDIT_CARD"), APPLICATION_JSON));

        DadosPagamento dados = provedor.criar(nova(cobranca, FormaPagamento.CARTAO_CREDITO));

        assertThat(dados).isEqualTo(new DadosPagamento("pay_3", null, null, "https://sandbox.asaas.com/i/pay_3"));
        asaas.verify();
    }

    @Test
    void erroDoAsaasTrazADescricao() {
        UUID cobranca = UUID.randomUUID();
        asaas.expect(requestTo(URL + "/payments?externalReference=" + cobranca)).andRespond(withSuccess(lista(), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/customers?externalReference=" + nutricionista + "&limit=1"))
                .andRespond(withSuccess(lista(), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/customers")).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(APPLICATION_JSON)
                .body("{\"errors\":[{\"code\":\"invalid_cpfCnpj\",\"description\":\"O CPF/CNPJ informado é inválido.\"}]}"));

        assertThatThrownBy(() -> provedor.criar(nova(cobranca, FormaPagamento.PIX)))
                .isInstanceOf(ErroAsaasException.class)
                .hasMessageContaining("POST /v3/customers respondeu 400")
                .hasMessageContaining("O CPF/CNPJ informado é inválido.")
                .hasMessageNotContaining(CHAVE);
    }

    // ------------------------------------------------------------------ cancelamento e estorno

    @Test
    void cancelarRemovePendenteEIgnoraJaRemovidaOuDesconhecida() {
        asaas.expect(requestTo(URL + "/payments/pay_1")).andExpect(method(GET))
                .andRespond(withSuccess(pagamento("pay_1", "OVERDUE", "PIX"), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/payments/pay_1")).andExpect(method(DELETE))
                .andRespond(withSuccess("{\"deleted\":true,\"id\":\"pay_1\"}", APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/payments/pay_2")).andExpect(method(GET))
                .andRespond(withSuccess(pagamento("pay_2", "PENDING", "PIX", true), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/payments/pay_3")).andExpect(method(GET)).andRespond(withResourceNotFound());

        provedor.cancelar("pay_1");
        provedor.cancelar("pay_2");
        provedor.cancelar("pay_3");
        asaas.verify();
    }

    @Test
    void cancelarCobrancaPagaLancaCobrancaJaPaga() {
        asaas.expect(requestTo(URL + "/payments/pay_1"))
                .andRespond(withSuccess(pagamento("pay_1", "CONFIRMED", "CREDIT_CARD"), APPLICATION_JSON));

        assertThatThrownBy(() -> provedor.cancelar("pay_1")).isInstanceOf(CobrancaJaPagaException.class);
        asaas.verify();
    }

    @Test
    void estornoDePixPelaApiEIdempotente() {
        asaas.expect(requestTo(URL + "/payments/pay_1"))
                .andRespond(withSuccess(pagamento("pay_1", "RECEIVED", "PIX"), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/payments/pay_1/refund")).andExpect(method(POST))
                .andExpect(jsonPath("$.value").value(89.90))
                .andExpect(jsonPath("$.description").value("Estorno solicitado no AFitCenter"))
                .andRespond(withSuccess(pagamento("pay_1", "REFUND_REQUESTED", "PIX"), APPLICATION_JSON));
        asaas.expect(requestTo(URL + "/payments/pay_1"))
                .andRespond(withSuccess(pagamento("pay_1", "REFUND_REQUESTED", "PIX"), APPLICATION_JSON));

        provedor.estornar("pay_1", new BigDecimal("89.90"));
        provedor.estornar("pay_1", new BigDecimal("89.90"));
        asaas.verify();
    }

    @Test
    void boletoNaoEEstornadoPelaApi() {
        asaas.expect(requestTo(URL + "/payments/pay_1"))
                .andRespond(withSuccess(pagamento("pay_1", "RECEIVED", "BOLETO"), APPLICATION_JSON));

        assertThatThrownBy(() -> provedor.estornar("pay_1", new BigDecimal("89.90")))
                .isInstanceOf(EstornoIndisponivelException.class)
                .hasMessageContaining("painel do Asaas");
        asaas.verify();
    }

    // ------------------------------------------------------------------ webhook

    @Test
    void webhookComTokenValidoViraPagamentoConfirmado() {
        NotificacaoPagamento notificacao = provedor.interpretar(Map.of(ProvedorAsaas.HEADER_TOKEN, TOKEN),
                evento("evt_1", "PAYMENT_RECEIVED", "pay_1"));

        assertThat(notificacao.idEvento()).isEqualTo("evt_1");
        assertThat(notificacao.idExterno()).isEqualTo("pay_1");
        assertThat(notificacao.tipo()).isEqualTo(Tipo.PAGAMENTO_CONFIRMADO);
        assertThat(notificacao.valorPago()).isEqualByComparingTo("89.90");
        assertThat(notificacao.ocorridaEm()).as("horário de Brasília").isEqualTo(Instant.parse("2026-10-18T15:30:00Z"));
    }

    @Test
    void webhookSemTokenOuComTokenErradoERecusado() {
        String corpo = evento("evt_1", "PAYMENT_RECEIVED", "pay_1");

        assertThatThrownBy(() -> provedor.interpretar(Map.of(), corpo)).isInstanceOf(NotificacaoInvalidaException.class);
        assertThatThrownBy(() -> provedor.interpretar(Map.of(ProvedorAsaas.HEADER_TOKEN, TOKEN + "x"), corpo))
                .isInstanceOf(NotificacaoInvalidaException.class);
        assertThatThrownBy(() -> provedor.interpretar(Map.of(ProvedorAsaas.HEADER_TOKEN, CHAVE), corpo))
                .as("a chave de API não serve de token").isInstanceOf(NotificacaoInvalidaException.class);
        assertThatThrownBy(() -> provedor.interpretar(Map.of(ProvedorAsaas.HEADER_TOKEN, TOKEN), "{não é json"))
                .isInstanceOf(NotificacaoInvalidaException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "PAYMENT_CONFIRMED, PAGAMENTO_CONFIRMADO",
            "PAYMENT_RECEIVED, PAGAMENTO_CONFIRMADO",
            "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED, PAGAMENTO_RECUSADO",
            "PAYMENT_REPROVED_BY_RISK_ANALYSIS, PAGAMENTO_RECUSADO",
            "PAYMENT_REFUNDED, ESTORNO",
            "PAYMENT_PARTIALLY_REFUNDED, IGNORADA",
            "PAYMENT_CREATED, IGNORADA",
            "PAYMENT_OVERDUE, IGNORADA",
            "PAYMENT_DELETED, IGNORADA",
            "PAYMENT_CHARGEBACK_REQUESTED, IGNORADA"
    })
    void eventosDoAsaas(String evento, Tipo esperado) {
        NotificacaoPagamento notificacao = provedor.interpretar(Map.of(ProvedorAsaas.HEADER_TOKEN, TOKEN),
                evento("evt_" + evento, evento, "pay_1"));

        assertThat(notificacao.tipo()).isEqualTo(esperado);
        assertThat(notificacao.valorPago() != null).isEqualTo(esperado == Tipo.PAGAMENTO_CONFIRMADO);
    }

    @Test
    void eventoSemCobrancaEIgnorado() {
        NotificacaoPagamento notificacao = provedor.interpretar(Map.of(ProvedorAsaas.HEADER_TOKEN, TOKEN),
                "{\"id\":\"evt_9\",\"event\":\"TRANSFER_DONE\",\"dateCreated\":\"2026-10-18 12:30:00\",\"transfer\":{}}");

        assertThat(notificacao.tipo()).isEqualTo(Tipo.IGNORADA);
        assertThat(notificacao.idExterno()).isNull();
        assertThat(notificacao.idEvento()).isEqualTo("evt_9");
    }

    @Test
    void exigeChaveETokenForte() {
        RestClient.Builder http = RestClient.builder();
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(() -> new ProvedorAsaas(http, json, clock, URL, " ", TOKEN))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chave-api");
        assertThatThrownBy(() -> new ProvedorAsaas(http, json, clock, URL, CHAVE, "curto"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("token-webhook");
    }

    // ------------------------------------------------------------------ apoio

    private NovaCobrancaProvedor nova(UUID cobranca, FormaPagamento forma) {
        return new NovaCobrancaProvedor(cobranca, "AFitCenter - assinatura", new BigDecimal("89.90"),
                LocalDate.parse("2026-10-21"), forma, new Pagador(nutricionista, "Ana Nutri", "52998224725",
                "ana@teste.local"));
    }

    private static String lista(String... itens) {
        return "{\"object\":\"list\",\"hasMore\":false,\"totalCount\":" + itens.length + ",\"limit\":10,\"offset\":0,"
                + "\"data\":[" + String.join(",", itens) + "]}";
    }

    private static String pagamento(String id, String status, String forma) {
        return pagamento(id, status, forma, false);
    }

    private static String pagamento(String id, String status, String forma, boolean removido) {
        return """
                {"object":"payment","id":"%s","customer":"cus_1","value":89.90,"netValue":88.91,"billingType":"%s",
                 "status":"%s","dueDate":"2026-10-21","invoiceUrl":"https://sandbox.asaas.com/i/%s","deleted":%s}
                """.formatted(id, forma, status, id, removido);
    }

    private static String evento(String id, String evento, String pagamento) {
        return """
                {"id":"%s","event":"%s","dateCreated":"2026-10-18 12:30:00",
                 "payment":{"object":"payment","id":"%s","value":89.90,"netValue":88.91,"billingType":"PIX",
                            "status":"RECEIVED","paymentDate":"2026-10-18"}}
                """.formatted(id, evento, pagamento);
    }
}
