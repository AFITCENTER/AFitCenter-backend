package br.com.afitcenter.pagamentos.provedor.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Cliente HTTP da API v3 do Asaas, só com o que o ms-pagamentos usa. A chave vai no header {@code access_token};
 * respostas 4xx/5xx viram {@link ErroAsaasException} com as descrições devolvidas pelo Asaas.
 *
 * <p>O Asaas não aceita chave de idempotência: quem chama procura pela {@code externalReference} antes de criar.</p>
 */
class AsaasApi {

    /** Obrigatório para contas criadas a partir de 13/06/2024. */
    static final String USER_AGENT = "AFitCenter-ms-pagamentos";

    private final RestClient rest;
    private final ObjectMapper objectMapper;

    AsaasApi(RestClient.Builder builder, ObjectMapper objectMapper, String url, String chaveApi) {
        this.objectMapper = objectMapper;
        this.rest = builder
                .baseUrl(url)
                .defaultHeader("access_token", chaveApi)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(HttpStatusCode::isError, (requisicao, resposta) -> {
                    throw erro(requisicao.getMethod() + " " + requisicao.getURI().getPath(), resposta);
                })
                .build();
    }

    // ------------------------------------------------------------------ clientes

    Optional<Cliente> clientePorReferencia(String referencia) {
        Lista<Cliente> lista = rest.get()
                .uri(u -> u.path("/customers").queryParam("externalReference", referencia).queryParam("limit", 1).build())
                .retrieve()
                .body(new ParameterizedTypeReference<Lista<Cliente>>() {
                });
        return lista == null ? Optional.empty() : lista.data().stream().filter(c -> !c.deleted()).findFirst();
    }

    Cliente criarCliente(NovoCliente cliente) {
        return rest.post().uri("/customers").contentType(MediaType.APPLICATION_JSON).body(cliente)
                .retrieve().body(Cliente.class);
    }

    // ------------------------------------------------------------------ cobranças

    List<Pagamento> pagamentosPorReferencia(String referencia) {
        Lista<Pagamento> lista = rest.get()
                .uri(u -> u.path("/payments").queryParam("externalReference", referencia).build())
                .retrieve()
                .body(new ParameterizedTypeReference<Lista<Pagamento>>() {
                });
        return lista == null ? List.of() : lista.data();
    }

    Pagamento criarPagamento(NovoPagamento pagamento) {
        return rest.post().uri("/payments").contentType(MediaType.APPLICATION_JSON).body(pagamento)
                .retrieve().body(Pagamento.class);
    }

    /** Vazio se o Asaas não conhece a cobrança. */
    Optional<Pagamento> pagamento(String id) {
        try {
            return Optional.ofNullable(rest.get().uri("/payments/{id}", id).retrieve().body(Pagamento.class));
        } catch (ErroAsaasException e) {
            if (e.naoEncontrado()) return Optional.empty();
            throw e;
        }
    }

    PixQrCode pixQrCode(String id) {
        return rest.get().uri("/payments/{id}/pixQrCode", id).retrieve().body(PixQrCode.class);
    }

    LinhaDigitavel linhaDigitavel(String id) {
        return rest.get().uri("/payments/{id}/identificationField", id).retrieve().body(LinhaDigitavel.class);
    }

    /** Remove a cobrança (não é estorno). Já removida ou inexistente não é erro. */
    void excluir(String id) {
        try {
            rest.delete().uri("/payments/{id}", id).retrieve().toBodilessEntity();
        } catch (ErroAsaasException e) {
            if (!e.naoEncontrado()) throw e;
        }
    }

    /** Pix e cartão. Sem valor, estorna o total. */
    void estornar(String id, BigDecimal valor, String descricao) {
        rest.post().uri("/payments/{id}/refund", id).contentType(MediaType.APPLICATION_JSON)
                .body(new Estorno(valor, descricao)).retrieve().toBodilessEntity();
    }

    // ------------------------------------------------------------------ apoio

    private ErroAsaasException erro(String chamada, ClientHttpResponse resposta) throws IOException {
        int status = resposta.getStatusCode().value();
        String corpo = new String(resposta.getBody().readAllBytes(), StandardCharsets.UTF_8);
        String descricoes;
        try {
            JsonNode erros = objectMapper.readTree(corpo).path("errors");
            descricoes = StreamSupport.stream(erros.spliterator(), false)
                    .map(e -> e.path("code").asText("") + ": " + e.path("description").asText(""))
                    .collect(Collectors.joining("; "));
        } catch (IOException e) {
            descricoes = "";
        }
        return new ErroAsaasException(status, chamada + " respondeu " + status
                + (descricoes.isEmpty() ? "" : " (" + descricoes + ")"));
    }

    static class ErroAsaasException extends RuntimeException {

        private final int status;

        ErroAsaasException(int status, String mensagem) {
            super(mensagem);
            this.status = status;
        }

        boolean naoEncontrado() {
            return status == 404;
        }
    }

    // ------------------------------------------------------------------ JSON

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Lista<T>(List<T> data) {

        Lista {
            data = data == null ? List.of() : data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cliente(String id, boolean deleted) {
    }

    /** {@code notificationDisabled}: o AFitCenter mostra a cobrança; o Asaas não manda e-mail nem SMS (pagos). */
    record NovoCliente(String name, String cpfCnpj, String email, String externalReference,
                       boolean notificationDisabled) {
    }

    /** {@code dueDate} em {@code yyyy-MM-dd}; {@code billingType}: PIX, BOLETO ou CREDIT_CARD. */
    record NovoPagamento(String customer, String billingType, BigDecimal value, String dueDate, String description,
                         String externalReference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pagamento(String id, String status, String billingType, String externalReference, String invoiceUrl,
                     boolean deleted) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PixQrCode(String payload) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LinhaDigitavel(String identificationField) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Estorno(BigDecimal value, String description) {
    }
}
