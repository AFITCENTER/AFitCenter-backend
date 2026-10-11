package br.com.afitcenter.avaliacao;

import br.com.afitcenter.avaliacao.integracao.ConsultaInternoClient;
import br.com.afitcenter.core.mensageria.evento.PacienteEvento;
import br.com.afitcenter.core.mensageria.evento.TipoAlteracao;
import br.com.afitcenter.core.paciente.PacienteEventosListener;
import br.com.afitcenter.core.paciente.PacienteInternoClient;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Base dos testes do ms-avaliacao. Todas as classes declaram os mesmos mocks, então compartilham um
 * único contexto Spring (e um único PostgreSQL embutido). Cada teste usa nutricionistas novos.
 */
@TesteIntegracao
abstract class IntegracaoBase {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private PacienteEventosListener pacienteListener;

    @MockitoBean
    protected PacienteInternoClient pacienteClient;
    @MockitoBean
    protected ConsultaInternoClient consultaClient;

    protected final UUID ana = UUID.randomUUID();
    protected final UUID bruno = UUID.randomUUID();

    /** Paciente na réplica, como se o evento do ms-paciente tivesse chegado. */
    protected UUID paciente(UUID nutricionista) {
        UUID id = UUID.randomUUID();
        pacienteListener.receber(new PacienteEvento(UUID.randomUUID(), TipoAlteracao.CRIADO, Instant.now(), id,
                nutricionista, "Paciente", "M", LocalDate.of(1995, 3, 10), "ATIVO", false, 0));
        return id;
    }

    protected ResultActions chamar(UUID nutricionista, MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao.header("Authorization", TokensTeste.nutricionista(nutricionista)));
    }

    protected ResultActions enviar(UUID nutricionista, MockHttpServletRequestBuilder requisicao, Object corpo)
            throws Exception {
        return chamar(nutricionista, requisicao.contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)));
    }

    protected UUID idDe(ResultActions resultado) throws Exception {
        return UUID.fromString(corpoDe(resultado).get("id").asText());
    }

    protected int idInteiroDe(ResultActions resultado) throws Exception {
        return corpoDe(resultado).get("id").asInt();
    }

    // ------------------------------------------------------------------ corpos

    /** Avaliação de bioimpedância com altura de 173 cm (a do laudo de referência). */
    protected ObjectNode avaliacao(UUID paciente, String dataHora, String peso, ObjectNode... resultados) {
        ObjectNode corpo = json.createObjectNode()
                .put("pacienteId", paciente.toString())
                .put("metodo", "BIOIMPEDANCIA")
                .put("dataHoraMedicao", dataHora)
                .put("pesoKg", new BigDecimal(peso))
                .put("alturaCm", new BigDecimal("173.0"))
                .put("emJejum", true);
        corpo.putArray("resultados").addAll(List.of(resultados));
        return corpo;
    }

    protected ObjectNode resultado(String metrica, String regiao, String valor) {
        ObjectNode r = json.createObjectNode().put("metricaCodigo", metrica).put("valor", new BigDecimal(valor));
        if (regiao != null) r.put("regiaoCodigo", regiao);
        return r;
    }

    protected ObjectNode exame(UUID paciente, String nome, String dataColeta, ObjectNode... resultados) {
        ObjectNode corpo = json.createObjectNode()
                .put("pacienteId", paciente.toString())
                .put("nome", nome)
                .put("laboratorio", "Laboratório Exemplo")
                .put("dataColeta", dataColeta);
        corpo.putArray("resultados").addAll(List.of(resultados));
        return corpo;
    }

    protected ObjectNode resultadoExame(String metrica, String valor) {
        return json.createObjectNode().put("metricaCodigo", metrica).put("valor", new BigDecimal(valor));
    }

    protected ObjectNode meta(String metrica, String valorAlvo, String dataInicio, String prazo) {
        ObjectNode corpo = json.createObjectNode().put("metricaCodigo", metrica)
                .put("valorAlvo", new BigDecimal(valorAlvo));
        if (dataInicio != null) corpo.put("dataInicio", dataInicio);
        if (prazo != null) corpo.put("prazo", prazo);
        return corpo;
    }

    private JsonNode corpoDe(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
