package br.com.afitcenter.nutricionista;

import br.com.afitcenter.core.assinatura.AssinaturaEventosListener;
import br.com.afitcenter.core.assinatura.AssinaturaInternoClient;
import br.com.afitcenter.core.mensageria.evento.AssinaturaEvento;
import br.com.afitcenter.core.mensageria.evento.TipoAlteracao;
import br.com.afitcenter.core.teste.DadosTeste;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import br.com.afitcenter.nutricionista.client.AutenticacaoClient;
import br.com.afitcenter.nutricionista.model.dto.Dtos.CriarUsuarioRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.UsuarioCriadoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteIntegracao
class NutricionistaIntegracaoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AssinaturaEventosListener assinaturaListener;

    @MockitoBean
    AutenticacaoClient autenticacaoClient;
    @MockitoBean
    AssinaturaInternoClient assinaturaClient;

    @Test
    void cadastroCriaCredencialEPerfilComOMesmoId() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(autenticacaoClient.criar(any())).thenReturn(new UsuarioCriadoResponse(usuarioId));

        String crn = "4" + System.nanoTime() % 100000;
        String email = DadosTeste.email("Ana");
        cadastrar(cadastro(DadosTeste.cpf(), crn, email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nutricionistaId").value(usuarioId.toString()));

        ArgumentCaptor<CriarUsuarioRequest> enviado = ArgumentCaptor.forClass(CriarUsuarioRequest.class);
        verify(autenticacaoClient).criar(enviado.capture());
        assertThat(enviado.getValue().perfil()).isEqualTo("NUTRICIONISTA");
        assertThat(enviado.getValue().email()).isEqualTo(email.toLowerCase());
        assertThat(enviado.getValue().aceites()).hasSize(2);

        mvc.perform(get("/v1/nutricionistas/me").header("Authorization", TokensTeste.nutricionista(usuarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crn").value("CRN-3 " + crn))
                .andExpect(jsonPath("$.email").value(email.toLowerCase()));
    }

    @Test
    void cpfDuplicadoRespondeConflitoSemCriarCredencial() throws Exception {
        String cpf = DadosTeste.cpf();
        when(autenticacaoClient.criar(any())).thenReturn(new UsuarioCriadoResponse(UUID.randomUUID()));
        cadastrar(cadastro(cpf, "51" + System.nanoTime() % 100000, DadosTeste.email("a"))).andExpect(status().isCreated());

        org.mockito.Mockito.clearInvocations(autenticacaoClient);
        cadastrar(cadastro(cpf, "52" + System.nanoTime() % 100000, DadosTeste.email("b")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe um nutricionista cadastrado com este CPF."));
        verify(autenticacaoClient, never()).criar(any());
    }

    @Test
    void emailJaUsadoNoMsAutenticacaoViraConflito() throws Exception {
        when(autenticacaoClient.criar(any())).thenThrow(new FeignException.Conflict("conflito",
                Request.create(Request.HttpMethod.POST, "/internal/usuarios", Map.of(), null, StandardCharsets.UTF_8, null),
                null, Map.of()));

        cadastrar(cadastro(DadosTeste.cpf(), "61" + System.nanoTime() % 100000, DadosTeste.email("c")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma conta com este e-mail."));
    }

    @Test
    void falhaAoGravarOPerfilRemoveACredencialCriada() throws Exception {
        // Mesmo id de um perfil já existente: o INSERT viola a chave primária depois da credencial criada.
        UUID existente = UUID.randomUUID();
        when(autenticacaoClient.criar(any())).thenReturn(new UsuarioCriadoResponse(existente));
        cadastrar(cadastro(DadosTeste.cpf(), "71" + System.nanoTime() % 100000, DadosTeste.email("d")))
                .andExpect(status().isCreated());

        cadastrar(cadastro(DadosTeste.cpf(), "72" + System.nanoTime() % 100000, DadosTeste.email("e")))
                .andExpect(status().isConflict());
        verify(autenticacaoClient).remover(existente);
    }

    @Test
    void cpfInvalidoESemAceiteRespondem400() throws Exception {
        ObjectNode corpo = cadastro("12345678900", "81", DadosTeste.email("f"));
        corpo.put("aceiteTermos", false);
        cadastrar(corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.cpf").value("CPF inválido."))
                .andExpect(jsonPath("$.erros.aceiteTermos").exists());
    }

    @Test
    void consultoriosIsoladosPorNutricionistaComEventosNaOutbox() throws Exception {
        UUID ana = criarNutricionista();
        UUID bruno = criarNutricionista();
        String tokenAna = TokensTeste.nutricionista(ana);

        String criado = mvc.perform(post("/v1/consultorios").header("Authorization", tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Sala 1\",\"endereco\":{\"cep\":\"01310-100\",\"uf\":\"SP\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endereco.cep").value("01310100"))
                .andReturn().getResponse().getContentAsString();
        UUID consultorio = UUID.fromString(json.readTree(criado).get("id").asText());

        mvc.perform(get("/v1/consultorios/" + consultorio).header("Authorization", TokensTeste.nutricionista(bruno)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/consultorios").header("Authorization", TokensTeste.nutricionista(bruno)))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(put("/v1/consultorios/" + consultorio).header("Authorization", tokenAna)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Sala 2\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/v1/consultorios/" + consultorio).header("Authorization", tokenAna))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/consultorios/" + consultorio).header("Authorization", tokenAna))
                .andExpect(status().isNotFound());

        var eventos = jdbc.queryForList("""
                SELECT routing_key, payload->>'versao' AS versao, payload->>'nome' AS nome
                FROM outbox_evento WHERE agregado_id = ? ORDER BY criado_em""", consultorio);
        assertThat(eventos).extracting(e -> e.get("routing_key"))
                .containsExactly("consultorio.criado", "consultorio.atualizado", "consultorio.desativado");
        assertThat(eventos).extracting(e -> e.get("versao")).containsExactly("0", "1", "2");
        assertThat(eventos.get(1).get("nome")).isEqualTo("Sala 2");

        mvc.perform(get("/internal/consultorios/" + consultorio).header("X-Internal-Token", "token-interno-teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.nutricionistaId").value(ana.toString()));
    }

    @Test
    void limiteDeConsultoriosDoPlano() throws Exception {
        UUID ana = criarNutricionista();
        String token = TokensTeste.nutricionista(ana);
        assinaturaListener.receber(new AssinaturaEvento(UUID.randomUUID(), TipoAlteracao.CRIADO, Instant.now(),
                UUID.randomUUID(), ana, "GRATUITO", "ATIVA", 10, 1, 0));

        String primeiro = mvc.perform(post("/v1/consultorios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Sala 1\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/v1/consultorios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Sala 2\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Seu plano permite até 1 consultórios. Mude de plano para cadastrar mais."));

        mvc.perform(delete("/v1/consultorios/" + json.readTree(primeiro).get("id").asText()).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(post("/v1/consultorios").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Sala 2\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void cadastroPublicoEntraNaTrilhaComONovoUsuario() throws Exception {
        UUID id = criarNutricionista();

        var registro = json.readTree(jdbc.queryForObject("""
                SELECT payload::text FROM outbox_evento
                WHERE routing_key = 'auditoria.registro' AND payload->>'rota' = '/v1/cadastros'
                  AND payload->>'usuarioId' = ?""", String.class, id.toString()));
        assertThat(registro.get("papel").asText()).isEqualTo("ANONIMO");
        assertThat(registro.get("nutricionistaId").asText()).isEqualTo(id.toString());
        assertThat(registro.toString()).doesNotContain("senha-forte-1").doesNotContain("@teste.local");
    }

    @Test
    void pagadorDaAssinaturaPelaRotaInterna() throws Exception {
        UUID id = UUID.randomUUID();
        when(autenticacaoClient.criar(any())).thenReturn(new UsuarioCriadoResponse(id));
        String cpf = DadosTeste.cpf();
        String email = DadosTeste.email("pagadora");
        cadastrar(cadastro(cpf, String.valueOf(System.nanoTime() % 10_000_000), email)).andExpect(status().isCreated());

        mvc.perform(get("/internal/nutricionistas/" + id).header("X-Internal-Token", "token-interno-teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nutri Teste"))
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.crnNumero").doesNotExist());
        mvc.perform(get("/internal/nutricionistas/" + id).header("Authorization", TokensTeste.nutricionista(id)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/nutricionistas/" + UUID.randomUUID()).header("X-Internal-Token", "token-interno-teste"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotasDoNutricionistaExigemPapelNutricionista() throws Exception {
        mvc.perform(get("/v1/nutricionistas/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/nutricionistas/me").header("Authorization",
                        TokensTeste.comPapeis(UUID.randomUUID(), br.com.afitcenter.core.base.security.Papel.PACIENTE)))
                .andExpect(status().isForbidden());
    }

    private UUID criarNutricionista() throws Exception {
        UUID id = UUID.randomUUID();
        when(autenticacaoClient.criar(any())).thenReturn(new UsuarioCriadoResponse(id));
        cadastrar(cadastro(DadosTeste.cpf(), String.valueOf(System.nanoTime() % 10_000_000), DadosTeste.email("n")))
                .andExpect(status().isCreated());
        return id;
    }

    private ObjectNode cadastro(String cpf, String crn, String email) {
        return json.createObjectNode()
                .put("nome", "Nutri Teste")
                .put("cpf", cpf)
                .put("crnRegiao", 3)
                .put("crnNumero", crn)
                .put("email", email)
                .put("senha", "senha-forte-1")
                .put("aceiteTermos", true)
                .put("versaoTermos", "1.0");
    }

    private ResultActions cadastrar(ObjectNode corpo) throws Exception {
        return mvc.perform(post("/v1/cadastros").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)));
    }
}
