package br.com.afitcenter.dieta;

import br.com.afitcenter.core.mensageria.evento.PacienteEvento;
import br.com.afitcenter.core.mensageria.evento.TipoAlteracao;
import br.com.afitcenter.core.paciente.PacienteEventosListener;
import br.com.afitcenter.core.paciente.PacienteInternoClient;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import br.com.afitcenter.dieta.integracao.ConsultaInternoClient;
import br.com.afitcenter.dieta.integracao.PacienteCondicaoClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes do ms-dieta: mesmo contexto (mesmos mocks) para todas as classes, dois nutricionistas
 * novos a cada teste e atalhos para montar dietas pela API.
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
    protected PacienteEventosListener pacienteListener;

    @MockitoBean
    protected PacienteInternoClient pacienteClient;
    @MockitoBean
    protected ConsultaInternoClient consultaClient;
    @MockitoBean
    protected PacienteCondicaoClient condicaoClient;

    protected final UUID ana = UUID.randomUUID();
    protected final UUID bruno = UUID.randomUUID();
    /** Os alimentos da base oficial são de todos: o sufixo separa os de cada teste na busca. */
    protected final String sufixo = UUID.randomUUID().toString().substring(0, 8);

    // ------------------------------------------------------------------ HTTP

    protected ResultActions getComo(UUID nutricionista, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", TokensTeste.nutricionista(nutricionista)));
    }

    protected ResultActions postComo(UUID nutricionista, String url, String corpo) throws Exception {
        return mvc.perform(post(url).header("Authorization", TokensTeste.nutricionista(nutricionista))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    protected ResultActions putComo(UUID nutricionista, String url, String corpo) throws Exception {
        return mvc.perform(put(url).header("Authorization", TokensTeste.nutricionista(nutricionista))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    protected ResultActions patchComo(UUID nutricionista, String url, String corpo) throws Exception {
        return mvc.perform(patch(url).header("Authorization", TokensTeste.nutricionista(nutricionista))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    protected ResultActions deleteComo(UUID nutricionista, String url) throws Exception {
        return mvc.perform(delete(url).header("Authorization", TokensTeste.nutricionista(nutricionista)));
    }

    protected String id(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    protected UUID uuid(ResultActions resultado) throws Exception {
        return UUID.fromString(id(resultado));
    }

    // ------------------------------------------------------------------ dados

    protected UUID paciente(UUID nutricionista) {
        return paciente(nutricionista, "Paciente " + sufixo, "ATIVO");
    }

    protected UUID paciente(UUID nutricionista, String nome, String status) {
        UUID id = UUID.randomUUID();
        pacienteListener.receber(new PacienteEvento(UUID.randomUUID(), TipoAlteracao.CRIADO, Instant.now(), id,
                nutricionista, nome, "F", LocalDate.of(1990, 1, 1), status, false, 0));
        return id;
    }

    /** Alimento da base oficial (TACO), direto no banco: a importação está fora deste serviço. */
    protected int alimentoOficial(String nome, Integer grupo, String porcaoG, String kcal, String proteinaG,
                                  String carboidratoG, String lipidioG, String fibraG) {
        return jdbc.queryForObject("""
                        INSERT INTO alimento (grupo_alimentar_id, nome, fonte, porcao_base_g, kcal, proteina_g,
                                              carboidrato_g, lipidio_g, fibra_g)
                        VALUES (?, ?, 'TACO', ?, ?, ?, ?, ?, ?) RETURNING id""", Integer.class,
                grupo, nome + " " + sufixo, decimal(porcaoG), decimal(kcal), decimal(proteinaG), decimal(carboidratoG),
                decimal(lipidioG), decimal(fibraG));
    }

    protected int alimentoOficial(String nome, Integer grupo) {
        return alimentoOficial(nome, grupo, "100", "100", "1", "1", "1", null);
    }

    protected int medidaOficial(int alimento, String descricao, String gramas) {
        return jdbc.queryForObject("INSERT INTO alimento_medida (alimento_id, descricao, gramas) VALUES (?, ?, ?) RETURNING id",
                Integer.class, alimento, descricao, decimal(gramas));
    }

    protected int alimentoProprio(UUID nutricionista, String nome) throws Exception {
        return Integer.parseInt(id(postComo(nutricionista, "/v1/alimentos", """
                {"nome": "%s %s", "grupoId": 13, "kcal": 200, "proteinaG": 10, "carboidratoG": 20, "lipidioG": 5}
                """.formatted(nome, sufixo)).andExpect(status().isCreated())));
    }

    protected UUID dieta(UUID nutricionista, UUID paciente) throws Exception {
        return uuid(postComo(nutricionista, "/v1/dietas", """
                {"pacienteId": "%s", "titulo": "Plano %s", "kcalMeta": 2000, "proteinaMetaG": 120}
                """.formatted(paciente, sufixo)).andExpect(status().isCreated()));
    }

    protected UUID modelo(UUID nutricionista, String titulo) throws Exception {
        return uuid(postComo(nutricionista, "/v1/dietas", """
                {"modelo": true, "titulo": "%s %s"}
                """.formatted(titulo, sufixo)).andExpect(status().isCreated()));
    }

    protected UUID refeicao(UUID nutricionista, UUID dieta, String nome, String diasSemana) throws Exception {
        String dias = diasSemana == null ? "" : ", \"diasSemana\": " + diasSemana;
        return uuid(postComo(nutricionista, "/v1/dietas/" + dieta + "/refeicoes", """
                {"nome": "%s"%s}""".formatted(nome, dias)).andExpect(status().isCreated()));
    }

    protected ResultActions adicionarItem(UUID nutricionista, UUID dieta, UUID refeicao, int alimento, String quantidade,
                                          Integer medida, int opcao) throws Exception {
        return postComo(nutricionista, itens(dieta, refeicao), """
                {"alimentoId": %d, "quantidade": %s, "alimentoMedidaId": %s, "opcao": %d}
                """.formatted(alimento, quantidade, medida, opcao));
    }

    protected UUID item(UUID nutricionista, UUID dieta, UUID refeicao, int alimento, String quantidade, Integer medida)
            throws Exception {
        return uuid(adicionarItem(nutricionista, dieta, refeicao, alimento, quantidade, medida, 1)
                .andExpect(status().isCreated()));
    }

    protected ResultActions adicionarSubstituto(UUID nutricionista, UUID dieta, UUID refeicao, UUID item, int alimento,
                                                String quantidade) throws Exception {
        return postComo(nutricionista, itens(dieta, refeicao) + "/" + item + "/substitutos", """
                {"alimentoId": %d, "quantidade": %s}""".formatted(alimento, quantidade));
    }

    protected static String itens(UUID dieta, UUID refeicao) {
        return "/v1/dietas/" + dieta + "/refeicoes/" + refeicao + "/itens";
    }

    private static BigDecimal decimal(String valor) {
        return valor == null ? null : new BigDecimal(valor);
    }
}
