package br.com.afitcenter.nutricionista;

import br.com.afitcenter.core.assinatura.AssinaturaEventosListener;
import br.com.afitcenter.core.assinatura.AssinaturaInternoClient;
import br.com.afitcenter.core.mensageria.evento.AssinaturaEvento;
import br.com.afitcenter.core.mensageria.evento.TipoAlteracao;
import br.com.afitcenter.core.teste.DadosTeste;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import br.com.afitcenter.nutricionista.client.PessoaClient;
import br.com.afitcenter.nutricionista.model.dto.Dtos.PessoaRegistradaResponse;
import br.com.afitcenter.nutricionista.model.dto.Dtos.RegistrarPessoaRequest;
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
import static org.mockito.Mockito.doThrow;
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
    PessoaClient pessoaClient;
    @MockitoBean
    AssinaturaInternoClient assinaturaClient;

    @Test
    void cadastroRegistraAPessoaEGravaOPerfilComOMesmoId() throws Exception {
        UUID pessoaId = UUID.randomUUID();
        when(pessoaClient.registrar(any())).thenReturn(new PessoaRegistradaResponse(pessoaId, true));

        String crn = "4" + System.nanoTime() % 100000;
        String cpf = DadosTeste.cpf();
        String email = DadosTeste.email("Ana");
        cadastrar(cadastro(formatado(cpf), crn, email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nutricionistaId").value(pessoaId.toString()));

        ArgumentCaptor<RegistrarPessoaRequest> enviado = ArgumentCaptor.forClass(RegistrarPessoaRequest.class);
        verify(pessoaClient).registrar(enviado.capture());
        assertThat(enviado.getValue().papel()).isEqualTo("NUTRICIONISTA");
        assertThat(enviado.getValue().nome()).isEqualTo("Nutri Teste");
        assertThat(enviado.getValue().cpf()).isEqualTo(cpf);
        assertThat(enviado.getValue().email()).isEqualTo(email);
        assertThat(enviado.getValue().aceites()).hasSize(2);

        mvc.perform(get("/v1/nutricionistas/me").header("Authorization", TokensTeste.nutricionista(pessoaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crn").value("CRN-3 " + crn))
                .andExpect(jsonPath("$.nome").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        mvc.perform(put("/v1/nutricionistas/me").header("Authorization", TokensTeste.nutricionista(pessoaId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"especialidades\":\"Esportiva\",\"endereco\":{\"cidade\":\"Santos\",\"uf\":\"SP\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades").value("Esportiva"))
                .andExpect(jsonPath("$.endereco.cidade").value("Santos"));
    }

    @Test
    void crnDuplicadoRespondeConflitoSemChamarOMsPessoa() throws Exception {
        String crn = "51" + System.nanoTime() % 100000;
        when(pessoaClient.registrar(any())).thenReturn(new PessoaRegistradaResponse(UUID.randomUUID(), true));
        cadastrar(cadastro(DadosTeste.cpf(), crn, DadosTeste.email("a"))).andExpect(status().isCreated());

        org.mockito.Mockito.clearInvocations(pessoaClient);
        cadastrar(cadastro(DadosTeste.cpf(), crn, DadosTeste.email("b")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe um nutricionista cadastrado com este CRN."));
        verify(pessoaClient, never()).registrar(any());
    }

    @Test
    void recusaDoMsPessoaChegaComOMotivo() throws Exception {
        doThrow(new FeignException.Conflict("conflito", requisicao(),
                problema(409, "Já existe uma conta com este CPF."), Map.of())).when(pessoaClient).registrar(any());
        cadastrar(cadastro(DadosTeste.cpf(), "61" + System.nanoTime() % 100000, DadosTeste.email("c")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma conta com este CPF."));

        doThrow(new FeignException.Conflict("conflito", requisicao(), null, Map.of())).when(pessoaClient).registrar(any());
        cadastrar(cadastro(DadosTeste.cpf(), "62" + System.nanoTime() % 100000, DadosTeste.email("c")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma conta com este e-mail ou CPF."));

        doThrow(new FeignException.TooManyRequests("bloqueado", requisicao(),
                problema(429, "Muitas tentativas com senha incorreta. Tente novamente em alguns minutos."), Map.of()))
                .when(pessoaClient).registrar(any());
        cadastrar(cadastro(DadosTeste.cpf(), "63" + System.nanoTime() % 100000, DadosTeste.email("c")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Muitas tentativas com senha incorreta. Tente novamente em alguns minutos."));
    }

    @Test
    void falhaAoGravarOPerfilTiraOPapelNoMsPessoa() throws Exception {
        // Mesmo id de um perfil já existente: o INSERT viola a chave primária depois da pessoa registrada.
        UUID existente = UUID.randomUUID();
        when(pessoaClient.registrar(any())).thenReturn(new PessoaRegistradaResponse(existente, true));
        cadastrar(cadastro(DadosTeste.cpf(), "71" + System.nanoTime() % 100000, DadosTeste.email("d")))
                .andExpect(status().isCreated());

        cadastrar(cadastro(DadosTeste.cpf(), "72" + System.nanoTime() % 100000, DadosTeste.email("e")))
                .andExpect(status().isConflict());
        verify(pessoaClient).removerPapel(existente, "NUTRICIONISTA");
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
    void rotasDoNutricionistaExigemPapelNutricionista() throws Exception {
        mvc.perform(get("/v1/nutricionistas/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/nutricionistas/me").header("Authorization",
                        TokensTeste.comPapeis(UUID.randomUUID(), br.com.afitcenter.core.base.security.Papel.PACIENTE)))
                .andExpect(status().isForbidden());
    }

    private UUID criarNutricionista() throws Exception {
        UUID id = UUID.randomUUID();
        when(pessoaClient.registrar(any())).thenReturn(new PessoaRegistradaResponse(id, true));
        cadastrar(cadastro(DadosTeste.cpf(), String.valueOf(System.nanoTime() % 10_000_000), DadosTeste.email("n")))
                .andExpect(status().isCreated());
        return id;
    }

    private static String formatado(String cpf) {
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private static Request requisicao() {
        return Request.create(Request.HttpMethod.POST, "/internal/pessoas", Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static byte[] problema(int status, String detalhe) {
        return ("{\"status\":" + status + ",\"detail\":\"" + detalhe + "\"}").getBytes(StandardCharsets.UTF_8);
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
