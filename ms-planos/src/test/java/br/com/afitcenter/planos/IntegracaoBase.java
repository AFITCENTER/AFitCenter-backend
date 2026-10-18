package br.com.afitcenter.planos;

import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.mensageria.evento.CobrancaEvento;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import br.com.afitcenter.planos.integracao.CobrancaInternoClient;
import br.com.afitcenter.planos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.CriarCobrancaRequest;
import br.com.afitcenter.planos.service.CicloAssinaturaService;
import br.com.afitcenter.planos.service.PagamentoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes do ms-planos: mesmo contexto para todas as classes, agendador desligado (os testes chamam o
 * {@link CicloAssinaturaService} com o instante que quiserem) e o ms-pagamentos simulado por mock.
 */
@TesteIntegracao
@TestPropertySource(properties = "afitcenter.planos.agendador.habilitado=false")
abstract class IntegracaoBase {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected PagamentoService pagamentos;
    @Autowired
    protected CicloAssinaturaService ciclo;

    @MockitoBean
    protected CobrancaInternoClient cobrancaClient;

    protected final UUID ana = UUID.randomUUID();
    protected final UUID bruno = UUID.randomUUID();

    @BeforeEach
    void cobrancasDoMsPagamentos() {
        when(cobrancaClient.criar(any())).thenAnswer(inv -> cobranca(inv.getArgument(0)));
    }

    protected static CobrancaResponse cobranca(CriarCobrancaRequest pedido) {
        return new CobrancaResponse(UUID.randomUUID(), "PENDENTE", pedido.formaPagamento(), pedido.valor(),
                pedido.vencimento(), "00020126SIMULADO", null, null);
    }

    // ------------------------------------------------------------------ HTTP

    protected ResultActions getComo(UUID nutricionista, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", TokensTeste.nutricionista(nutricionista)));
    }

    protected ResultActions postComo(UUID nutricionista, String url, String corpo) throws Exception {
        return mvc.perform(post(url).header("Authorization", TokensTeste.nutricionista(nutricionista))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    protected ResultActions deleteComo(UUID nutricionista, String url) throws Exception {
        return mvc.perform(delete(url).header("Authorization", TokensTeste.nutricionista(nutricionista)));
    }

    protected ResultActions comoAdmin(String metodo, String url, String corpo) throws Exception {
        var requisicao = switch (metodo) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            case "PATCH" -> patch(url);
            default -> get(url);
        };
        requisicao.header("Authorization", TokensTeste.comPapeis(UUID.randomUUID(), Papel.ADMIN));
        if (corpo != null) requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        return mvc.perform(requisicao);
    }

    protected JsonNode corpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ dados

    protected int planoId(String codigo) {
        return jdbc.queryForObject("SELECT id FROM plano WHERE codigo = ?", Integer.class, codigo);
    }

    /** Pede a mudança para um plano pago e devolve o id da fatura emitida. */
    protected UUID pedirMudanca(UUID nutricionista, String codigo, String ciclo) throws Exception {
        var resposta = postComo(nutricionista, "/v1/assinatura/mudanca-plano",
                "{\"planoId\":%d,\"ciclo\":\"%s\",\"formaPagamento\":\"PIX\"}".formatted(planoId(codigo), ciclo))
                .andExpect(status().isOk());
        return UUID.fromString(corpo(resposta).at("/fatura/id").asText());
    }

    protected UUID cobrancaDa(UUID fatura) {
        return jdbc.queryForObject("SELECT cobranca_id FROM fatura WHERE id = ?", UUID.class, fatura);
    }

    /** Simula o evento {@code cobranca.<status>} do ms-pagamentos para a cobrança vigente da fatura. */
    protected void eventoCobranca(UUID fatura, String status, Instant quando) {
        var linha = jdbc.queryForMap("SELECT nutricionista_id, valor, cobranca_id FROM fatura WHERE id = ?", fatura);
        pagamentos.receber(new CobrancaEvento(UUID.randomUUID(), Instant.now(), (UUID) linha.get("cobranca_id"),
                (UUID) linha.get("nutricionista_id"), "ASSINATURA", fatura, status, (BigDecimal) linha.get("valor"),
                "PAGA".equals(status) ? quando : null, 1));
    }

    /** Assinatura paga no plano e ciclo, com o pagamento confirmado em {@code pagoEm}. */
    protected UUID assinarPago(UUID nutricionista, String codigo, Instant pagoEm) throws Exception {
        UUID fatura = pedirMudanca(nutricionista, codigo, "MENSAL");
        eventoCobranca(fatura, "PAGA", pagoEm);
        return fatura;
    }

    protected Instant periodoFim(UUID nutricionista) {
        return jdbc.queryForObject("SELECT periodo_fim FROM assinatura WHERE nutricionista_id = ?",
                java.sql.Timestamp.class, nutricionista).toInstant();
    }

    protected String statusAssinatura(UUID nutricionista) {
        return jdbc.queryForObject("SELECT status FROM assinatura WHERE nutricionista_id = ?", String.class, nutricionista);
    }

    /** Último evento assinatura.* publicado (na outbox) para o nutricionista. */
    protected JsonNode ultimoEvento(UUID nutricionista) throws Exception {
        String payload = jdbc.queryForObject("""
                SELECT payload::text FROM outbox_evento
                WHERE routing_key LIKE 'assinatura.%' AND payload->>'nutricionistaId' = ?
                ORDER BY (payload->>'versao')::bigint DESC, criado_em DESC LIMIT 1""", String.class, nutricionista.toString());
        return json.readTree(payload);
    }
}
