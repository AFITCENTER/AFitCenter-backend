package br.com.afitcenter.pagamentos.provedor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Provedor de mentira para desenvolvimento e homologação: devolve Pix, boleto e página de cartão fictícios e
 * aceita notificações assinadas com HMAC-SHA256 ({@code X-Simulador-Assinatura}), no mesmo formato que um
 * provedor real usaria. Nenhum dinheiro se move.
 *
 * <p>O pagamento é disparado pelos endpoints {@code /v1/simulador/**} (só com
 * {@code afitcenter.pagamentos.simulador.endpoints-habilitados=true}) ou por um POST assinado em
 * {@code /v1/webhooks/simulado}.</p>
 */
@Component
@ConditionalOnProperty(name = "afitcenter.pagamentos.simulador.habilitado", havingValue = "true", matchIfMissing = true)
public class ProvedorSimulado implements ProvedorPagamento {

    public static final String NOME = "SIMULADO";
    public static final String HEADER_ASSINATURA = "X-Simulador-Assinatura";

    private final ObjectMapper objectMapper;
    private final byte[] segredo;
    private final String urlCheckout;

    public ProvedorSimulado(ObjectMapper objectMapper,
                            @Value("${afitcenter.pagamentos.simulador.segredo-webhook:}") String segredo,
                            @Value("${afitcenter.pagamentos.simulador.url-checkout:https://checkout.simulado.afitcenter.local}")
                            String urlCheckout) {
        if (segredo == null || segredo.length() < 16)
            throw new IllegalStateException("Defina afitcenter.pagamentos.simulador.segredo-webhook (16+ caracteres).");
        this.objectMapper = objectMapper;
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
        this.urlCheckout = urlCheckout;
    }

    @Override
    public String nome() {
        return NOME;
    }

    /** Determinístico pelo id da cobrança: repetir a criação devolve os mesmos dados. */
    @Override
    public DadosPagamento criar(NovaCobrancaProvedor cobranca) {
        String idExterno = "sim_" + cobranca.cobrancaId().toString().replace("-", "");
        return switch (cobranca.formaPagamento()) {
            case PIX -> new DadosPagamento(idExterno, pix(idExterno, cobranca.valor()), null, null);
            case BOLETO -> new DadosPagamento(idExterno, null, boleto(cobranca.cobrancaId()), null);
            case CARTAO_CREDITO -> new DadosPagamento(idExterno, null, null, urlCheckout + "/" + idExterno);
        };
    }

    @Override
    public void cancelar(String idExterno) {
        // Nada a fazer: o simulador não guarda estado.
    }

    @Override
    public void estornar(String idExterno, BigDecimal valor) {
        // Nada a fazer: a confirmação do estorno é aplicada direto pelo serviço.
    }

    @Override
    public NotificacaoPagamento interpretar(Map<String, String> headers, String corpo) {
        String recebida = headers.get(HEADER_ASSINATURA.toLowerCase());
        if (recebida == null || corpo == null
                || !MessageDigest.isEqual(assinar(corpo).getBytes(StandardCharsets.US_ASCII),
                recebida.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII)))
            throw new NotificacaoInvalidaException("Assinatura do webhook ausente ou inválida.");
        try {
            JsonNode json = objectMapper.readTree(corpo);
            NotificacaoPagamento.Tipo tipo;
            try {
                tipo = NotificacaoPagamento.Tipo.valueOf(json.path("tipo").asText());
            } catch (IllegalArgumentException e) {
                tipo = NotificacaoPagamento.Tipo.IGNORADA;
            }
            Instant quando = json.hasNonNull("ocorridaEm") ? Instant.parse(json.get("ocorridaEm").asText()) : Instant.now();
            BigDecimal valor = json.hasNonNull("valorPago") ? json.get("valorPago").decimalValue() : null;
            String idEvento = json.path("id").asText(null);
            String idExterno = json.path("cobranca").asText(null);
            if (idEvento == null || idExterno == null)
                throw new NotificacaoInvalidaException("Notificação sem id ou sem cobrança.");
            return new NotificacaoPagamento(idEvento, idExterno, tipo, quando, valor);
        } catch (NotificacaoInvalidaException e) {
            throw e;
        } catch (Exception e) {
            throw new NotificacaoInvalidaException("Corpo do webhook ilegível.");
        }
    }

    /** Corpo da notificação no formato do simulador, para os endpoints de simulação e os testes. */
    public String notificacao(String idExterno, NotificacaoPagamento.Tipo tipo, BigDecimal valorPago) {
        try {
            var corpo = objectMapper.createObjectNode()
                    .put("id", "evt_" + UUID.randomUUID().toString().replace("-", ""))
                    .put("cobranca", idExterno)
                    .put("tipo", tipo.name())
                    .put("ocorridaEm", Instant.now().toString());
            if (valorPago != null) corpo.put("valorPago", valorPago);
            return objectMapper.writeValueAsString(corpo);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** HMAC-SHA256 do corpo, em hexadecimal minúsculo. */
    public String assinar(String corpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Parece um Pix copia e cola (BR Code), mas o recebedor e o CRC são fictícios. */
    private static String pix(String idExterno, BigDecimal valor) {
        String valorTexto = valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String chave = "SIMULADO-" + idExterno;
        return "000201" + "26" + String.format("%02d", 22 + chave.length()) + "0014br.gov.bcb.pix01"
                + String.format("%02d", chave.length()) + chave + "52040000" + "5303986"
                + "54" + String.format("%02d", valorTexto.length()) + valorTexto + "5802BR"
                + "5919AFITCENTER SIMULADO" + "6009SAO PAULO" + "62070503***" + "63040000";
    }

    /** 47 dígitos no formato da linha digitável, sem valor bancário. */
    private static String boleto(UUID cobrancaId) {
        String digitos = String.format("%047d", new BigInteger(
                cobrancaId.toString().replace("-", ""), 16).mod(BigInteger.TEN.pow(47)));
        return "%s.%s %s.%s %s.%s %s %s".formatted(digitos.substring(0, 5), digitos.substring(5, 10),
                digitos.substring(10, 15), digitos.substring(15, 21), digitos.substring(21, 26),
                digitos.substring(26, 32), digitos.substring(32, 33), digitos.substring(33));
    }
}
