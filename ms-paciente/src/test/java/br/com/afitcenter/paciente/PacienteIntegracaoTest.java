package br.com.afitcenter.paciente;

import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.core.teste.DadosTeste;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteIntegracao
class PacienteIntegracaoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();

    @Test
    void cicloDeVidaCompletoPublicaEventosComVersao() throws Exception {
        UUID id = criar(ana, paciente("Carlos Silva", DadosTeste.cpf()));

        mvc.perform(get("/v1/pacientes/" + id).header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeExibicao").value("Cadu"))
                .andExpect(jsonPath("$.idade").isNumber())
                .andExpect(jsonPath("$.contatos[0].tipo").value("WHATSAPP"))
                .andExpect(jsonPath("$.endereco.cep").value("01310100"));

        ObjectNode alterado = paciente("Carlos Silva", null);
        alterado.put("objetivo", "Ganhar massa").put("status", "INATIVO");
        mvc.perform(put("/v1/pacientes/" + id).header("Authorization", TokensTeste.nutricionista(ana))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(alterado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVO"))
                .andExpect(jsonPath("$.cpf").doesNotExist());

        mvc.perform(delete("/v1/pacientes/" + id).header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/pacientes/" + id).header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isNotFound());

        var eventos = jdbc.queryForList("""
                SELECT routing_key, payload FROM outbox_evento WHERE agregado_id = ? ORDER BY criado_em""", id);
        assertThat(eventos).extracting(e -> e.get("routing_key"))
                .containsExactly("paciente.criado", "paciente.atualizado", "paciente.excluido");
        JsonNode ultimo = json.readTree(eventos.get(2).get("payload").toString());
        assertThat(ultimo.get("versao").asLong()).isEqualTo(2);
        assertThat(ultimo.get("excluido").asBoolean()).isTrue();
        assertThat(ultimo.get("nutricionistaId").asText()).isEqualTo(ana.toString());
        assertThat(ultimo.get("nome").asText()).as("evento leva o nome de exibição").isEqualTo("Cadu");

        // A réplica de outro serviço ainda consegue saber que ele foi excluído.
        mvc.perform(get("/internal/pacientes/" + id).header("X-Internal-Token", ChavesTeste.TOKEN_INTERNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excluido").value(true))
                .andExpect(jsonPath("$.versao").value(2));
    }

    @Test
    void pacienteDeOutroNutricionistaNaoApareceNemPodeSerAlterado() throws Exception {
        UUID id = criar(ana, paciente("Paciente da Ana", null));
        String tokenBruno = TokensTeste.nutricionista(bruno);

        mvc.perform(get("/v1/pacientes/" + id).header("Authorization", tokenBruno)).andExpect(status().isNotFound());
        mvc.perform(put("/v1/pacientes/" + id).header("Authorization", tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(paciente("Invasor", null))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/v1/pacientes/" + id).header("Authorization", tokenBruno)).andExpect(status().isNotFound());
        mvc.perform(get("/v1/pacientes/" + id + "/condicoes").header("Authorization", tokenBruno))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/pacientes").header("Authorization", tokenBruno))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void cpfUnicoPorNutricionista() throws Exception {
        String cpf = DadosTeste.cpf();
        criar(ana, paciente("Pessoa A", cpf));
        criar(bruno, paciente("Mesma pessoa no Bruno", cpf));

        cadastrar(ana, paciente("Pessoa A de novo", cpf))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Você já tem um paciente cadastrado com este CPF."));
    }

    @Test
    void buscaPorNomeNomeSocialOuCpfComPaginacao() throws Exception {
        String cpf = DadosTeste.cpf();
        criar(ana, paciente("Joana Prado", cpf));
        criar(ana, paciente("Joaquim Lemos", null));
        criar(ana, paciente("Pedro Alves", null));

        mvc.perform(get("/v1/pacientes").param("busca", "joa").header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Joana Prado"));
        mvc.perform(get("/v1/pacientes").param("busca", cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "."
                                + cpf.substring(6, 9) + "-" + cpf.substring(9))
                        .header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/v1/pacientes").param("busca", "cadu").param("tamanho", "2")
                        .header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.conteudo.length()").value(2));
    }

    @Test
    void condicoesDoCatalogoEPropriasComIsolamento() throws Exception {
        UUID pacienteId = criar(ana, paciente("Com restrições", null));
        String tokenAna = TokensTeste.nutricionista(ana);

        Integer lactose = jdbc.queryForObject(
                "SELECT id FROM condicao WHERE nome = 'Intolerância à lactose' AND nutricionista_id IS NULL", Integer.class);
        String propria = mvc.perform(post("/v1/condicoes").header("Authorization", tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoCondicaoId\":6,\"nome\":\"Não come coentro\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.propria").value(true))
                .andReturn().getResponse().getContentAsString();
        int coentro = json.readTree(propria).get("id").asInt();

        mvc.perform(get("/v1/condicoes").param("tipoId", "6").header("Authorization", TokensTeste.nutricionista(bruno)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/v1/condicoes/" + lactose).header("Authorization", tokenAna)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipoCondicaoId\":2,\"nome\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());

        vincular(pacienteId, lactose, "GRAVE").andExpect(status().isCreated())
                .andExpect(jsonPath("$.condicao.tipo").value("Intolerância alimentar"));
        vincular(pacienteId, coentro, null).andExpect(status().isCreated());
        vincular(pacienteId, lactose, null).andExpect(status().isConflict());

        mvc.perform(post("/v1/pacientes/" + pacienteId + "/condicoes").header("Authorization", TokensTeste.nutricionista(bruno))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condicaoId\":" + lactose + "}"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/internal/pacientes/" + pacienteId + "/condicoes-ativas")
                        .header("X-Internal-Token", ChavesTeste.TOKEN_INTERNO))
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(delete("/v1/condicoes/" + coentro).header("Authorization", tokenAna))
                .andExpect(status().isConflict());
    }

    @Test
    void medicamentosComValidacaoDePeriodo() throws Exception {
        UUID pacienteId = criar(ana, paciente("Usa remédio", null));
        String url = "/v1/pacientes/" + pacienteId + "/medicamentos";

        mvc.perform(post(url).header("Authorization", TokensTeste.nutricionista(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"MEDICAMENTO\",\"nome\":\"Metformina\",\"inicio\":\"2026-05-01\",\"fim\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.periodoValido").exists());

        mvc.perform(post(url).header("Authorization", TokensTeste.nutricionista(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"SUPLEMENTO\",\"nome\":\"Creatina\",\"dosagem\":\"3 g\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get(url).header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(jsonPath("$[0].nome").value("Creatina"));
    }

    @Test
    void validaCamposObrigatoriosEExigePapelNutricionista() throws Exception {
        cadastrar(ana, json.createObjectNode().put("nome", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").exists())
                .andExpect(jsonPath("$.erros.dataNascimento").exists())
                .andExpect(jsonPath("$.erros.sexo").exists());

        mvc.perform(get("/v1/pacientes").header("Authorization", TokensTeste.comPapeis(ana,
                        br.com.afitcenter.core.base.security.Papel.PACIENTE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/pacientes/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private ObjectNode paciente(String nome, String cpf) {
        ObjectNode corpo = json.createObjectNode()
                .put("nome", nome)
                .put("nomeSocial", "Cadu")
                .put("dataNascimento", "1995-03-10")
                .put("sexo", "M")
                .put("nivelAtividade", "LEVE");
        if (cpf != null) corpo.put("cpf", cpf);
        corpo.set("endereco", json.valueToTree(Map.of("cep", "01310-100", "cidade", "São Paulo", "uf", "SP")));
        corpo.set("contatos", json.createArrayNode().add(json.createObjectNode()
                .put("tipo", "WHATSAPP").put("valor", "11999990000").put("principal", true)));
        return corpo;
    }

    private ResultActions cadastrar(UUID nutricionista, ObjectNode corpo) throws Exception {
        return mvc.perform(post("/v1/pacientes").header("Authorization", TokensTeste.nutricionista(nutricionista))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)));
    }

    private UUID criar(UUID nutricionista, ObjectNode corpo) throws Exception {
        String resposta = cadastrar(nutricionista, corpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(resposta).get("id").asText());
    }

    private ResultActions vincular(UUID pacienteId, int condicaoId, String gravidade) throws Exception {
        ObjectNode corpo = json.createObjectNode().put("condicaoId", condicaoId);
        if (gravidade != null) corpo.put("gravidade", gravidade);
        return mvc.perform(post("/v1/pacientes/" + pacienteId + "/condicoes")
                .header("Authorization", TokensTeste.nutricionista(ana))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)));
    }
}
