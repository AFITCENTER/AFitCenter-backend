package br.com.afitcenter.pagamentos.provedor.asaas;

import br.com.afitcenter.pagamentos.provedor.DadosPagamento;
import br.com.afitcenter.pagamentos.provedor.NotificacaoPagamento;
import br.com.afitcenter.pagamentos.provedor.NovaCobrancaProvedor;
import br.com.afitcenter.pagamentos.provedor.Pagador;
import br.com.afitcenter.pagamentos.provedor.ProvedorPagamento;
import br.com.afitcenter.pagamentos.provedor.asaas.AsaasApi.Cliente;
import br.com.afitcenter.pagamentos.provedor.asaas.AsaasApi.NovoCliente;
import br.com.afitcenter.pagamentos.provedor.asaas.AsaasApi.NovoPagamento;
import br.com.afitcenter.pagamentos.provedor.asaas.AsaasApi.Pagamento;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/**
 * Asaas (API v3): Pix, boleto e cartão. O cartão é digitado na fatura hospedada do Asaas ({@code invoiceUrl}),
 * nunca no AFitCenter. Ligado por {@code afitcenter.pagamentos.asaas.habilitado=true}; as novas cobranças só vão
 * para ele com {@code afitcenter.pagamentos.provedor=ASAAS}.
 *
 * <ul>
 *     <li><b>Criação:</b> o pagador vira um cliente do Asaas com {@code externalReference} = id do nutricionista
 *     (reaproveitado nas próximas cobranças) e a cobrança leva {@code externalReference} = id da cobrança. O Asaas
 *     não aceita chave de idempotência, então antes de criar procura-se a cobrança por essa referência: repetir
 *     depois de um timeout devolve a mesma.</li>
 *     <li><b>Webhook:</b> autenticado pelo token configurado no painel do Asaas, que chega no header
 *     {@code asaas-access-token}; o {@code id} do evento identifica as reentregas.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "afitcenter.pagamentos.asaas.habilitado", havingValue = "true")
public class ProvedorAsaas implements ProvedorPagamento {

    public static final String NOME = "ASAAS";
    public static final String HEADER_TOKEN = "asaas-access-token";

    /** As datas dos eventos vêm sem fuso, no horário de Brasília. */
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_EVENTO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** A criação faz até 5 chamadas em sequência; o ms-planos espera até 30 s pela resposta do ms-pagamentos. */
    private static final Duration TIMEOUT_CONEXAO = Duration.ofSeconds(3);
    private static final Duration TIMEOUT_LEITURA = Duration.ofSeconds(6);

    /** Situações em que ainda dá para remover a cobrança; nas outras ela já foi paga (ou está em análise). */
    private static final Set<String> NAO_PAGAS = Set.of("PENDING", "OVERDUE");
    private static final Set<String> ESTORNO_PEDIDO = Set.of("REFUNDED", "REFUND_REQUESTED", "REFUND_IN_PROGRESS");

    private final AsaasApi api;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] tokenWebhook;

    @Autowired
    public ProvedorAsaas(ObjectMapper objectMapper, Clock clock,
                         @Value("${afitcenter.pagamentos.asaas.url}") String url,
                         @Value("${afitcenter.pagamentos.asaas.chave-api:}") String chaveApi,
                         @Value("${afitcenter.pagamentos.asaas.token-webhook:}") String tokenWebhook) {
        this(RestClient.builder().requestFactory(fabricaHttp()), objectMapper, clock, url, chaveApi, tokenWebhook);
        log.info("Provedor de pagamento Asaas disponível em {}.", url);
    }

    /** Para os testes, com o servidor do Asaas simulado no {@code http}. */
    ProvedorAsaas(RestClient.Builder http, ObjectMapper objectMapper, Clock clock, String url, String chaveApi,
                  String tokenWebhook) {
        if (chaveApi == null || chaveApi.isBlank())
            throw new IllegalStateException("Defina afitcenter.pagamentos.asaas.chave-api.");
        if (tokenWebhook == null || tokenWebhook.length() < 32)
            throw new IllegalStateException("Defina afitcenter.pagamentos.asaas.token-webhook (32+ caracteres).");
        this.api = new AsaasApi(http, objectMapper, url, chaveApi.trim());
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tokenWebhook = tokenWebhook.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public DadosPagamento criar(NovaCobrancaProvedor cobranca) {
        String referencia = cobranca.cobrancaId().toString();
        Pagamento pagamento = api.pagamentosPorReferencia(referencia).stream()
                .filter(p -> !p.deleted())
                .findFirst()
                .orElseGet(() -> api.criarPagamento(new NovoPagamento(cliente(cobranca.pagador()),
                        tipoCobranca(cobranca), cobranca.valor(), cobranca.vencimento().toString(),
                        cobranca.descricao(), referencia)));

        return switch (cobranca.formaPagamento()) {
            case PIX -> new DadosPagamento(pagamento.id(), api.pixQrCode(pagamento.id()).payload(), null,
                    pagamento.invoiceUrl());
            case BOLETO -> new DadosPagamento(pagamento.id(), null,
                    api.linhaDigitavel(pagamento.id()).identificationField(), pagamento.invoiceUrl());
            case CARTAO_CREDITO -> new DadosPagamento(pagamento.id(), null, null, pagamento.invoiceUrl());
        };
    }

    /** Idempotente: cobrança já removida (ou que o Asaas não conhece) não é erro. */
    @Override
    public void cancelar(String idExterno) {
        Pagamento pagamento = api.pagamento(idExterno).orElse(null);
        if (pagamento == null || pagamento.deleted()) return;
        if (!NAO_PAGAS.contains(pagamento.status())) throw new CobrancaJaPagaException(idExterno);
        api.excluir(idExterno);
    }

    /**
     * Pix e cartão são estornados pela API. Boleto não: o Asaas pede os dados bancários ao pagador por um
     * formulário, então o estorno é feito pelo painel e chega aqui pelo webhook {@code PAYMENT_REFUNDED}.
     */
    @Override
    public void estornar(String idExterno, BigDecimal valor) {
        Pagamento pagamento = api.pagamento(idExterno)
                .orElseThrow(() -> new IllegalStateException("O Asaas não encontrou a cobrança " + idExterno + "."));
        if (ESTORNO_PEDIDO.contains(pagamento.status())) return;
        if ("BOLETO".equals(pagamento.billingType()))
            throw new EstornoIndisponivelException("Boletos pagos no Asaas são estornados pelo painel do Asaas: o "
                    + "pagador informa a conta para a devolução. A cobrança fica estornada quando o Asaas confirmar.");
        api.estornar(idExterno, valor, "Estorno solicitado no AFitCenter");
    }

    @Override
    public NotificacaoPagamento interpretar(Map<String, String> headers, String corpo) {
        String recebido = headers.get(HEADER_TOKEN);
        if (recebido == null || corpo == null
                || !MessageDigest.isEqual(tokenWebhook, recebido.trim().getBytes(StandardCharsets.UTF_8)))
            throw new NotificacaoInvalidaException("Token do webhook do Asaas ausente ou inválido.");

        JsonNode json;
        try {
            json = objectMapper.readTree(corpo);
        } catch (Exception e) {
            throw new NotificacaoInvalidaException("Corpo do webhook ilegível.");
        }
        String evento = json.path("event").asText(null);
        if (evento == null) throw new NotificacaoInvalidaException("Notificação sem evento.");
        JsonNode pagamento = json.path("payment");
        String idExterno = pagamento.path("id").asText(null);
        // Eventos antigos não têm id; o mesmo evento da mesma cobrança só acontece uma vez nos que interessam.
        String idEvento = json.path("id").asText(evento + ":" + idExterno);

        NotificacaoPagamento.Tipo tipo = idExterno == null ? NotificacaoPagamento.Tipo.IGNORADA : tipo(evento);
        if (evento.startsWith("PAYMENT_CHARGEBACK"))
            log.warn("Chargeback ({}) na cobrança {} do Asaas: acompanhe pelo painel do Asaas.", evento, idExterno);
        BigDecimal valor = tipo == NotificacaoPagamento.Tipo.PAGAMENTO_CONFIRMADO && pagamento.hasNonNull("value")
                ? pagamento.get("value").decimalValue() : null;
        return new NotificacaoPagamento(idEvento, idExterno, tipo, quando(json.path("dateCreated").asText(null)), valor);
    }

    // ------------------------------------------------------------------ apoio

    /** O cliente do Asaas do pagador: o mesmo em todas as cobranças dele. */
    private String cliente(Pagador pagador) {
        if (pagador == null) throw new IllegalArgumentException("O Asaas exige os dados do pagador.");
        String referencia = pagador.referencia().toString();
        return api.clientePorReferencia(referencia)
                .map(Cliente::id)
                .orElseGet(() -> api.criarCliente(new NovoCliente(pagador.nome(), pagador.cpfCnpj(), pagador.email(),
                        referencia, true)).id());
    }

    private static String tipoCobranca(NovaCobrancaProvedor cobranca) {
        return switch (cobranca.formaPagamento()) {
            case PIX -> "PIX";
            case BOLETO -> "BOLETO";
            case CARTAO_CREDITO -> "CREDIT_CARD";
        };
    }

    /**
     * CONFIRMED (cartão aprovado, dinheiro ainda não liberado) e RECEIVED (Pix e boleto, ou o cartão quando o
     * dinheiro é liberado) contam como pagamento; o segundo deles é ignorado porque a cobrança já está paga.
     */
    private static NotificacaoPagamento.Tipo tipo(String evento) {
        return switch (evento) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> NotificacaoPagamento.Tipo.PAGAMENTO_CONFIRMADO;
            case "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED", "PAYMENT_REPROVED_BY_RISK_ANALYSIS" ->
                    NotificacaoPagamento.Tipo.PAGAMENTO_RECUSADO;
            case "PAYMENT_REFUNDED" -> NotificacaoPagamento.Tipo.ESTORNO;
            default -> NotificacaoPagamento.Tipo.IGNORADA;
        };
    }

    private Instant quando(String dataEvento) {
        if (dataEvento == null) return clock.instant();
        try {
            return LocalDateTime.parse(dataEvento, DATA_EVENTO).atZone(FUSO).toInstant();
        } catch (DateTimeParseException e) {
            return clock.instant();
        }
    }

    private static JdkClientHttpRequestFactory fabricaHttp() {
        var fabrica = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXAO).build());
        fabrica.setReadTimeout(TIMEOUT_LEITURA);
        return fabrica;
    }
}
