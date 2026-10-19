package br.com.afitcenter.autenticacao;

import br.com.afitcenter.core.auth.jwt.JwtVerificador;
import br.com.afitcenter.core.auth.jwt.TokenAcesso;
import br.com.afitcenter.core.base.http.InternalHeaders;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteIntegracao
@TestPropertySource(properties = {
        "afitcenter.jwt.chave-privada=" + ChavesTeste.PRIVADA,
        "afitcenter.login.max-falhas=3"
})
class AutenticacaoIntegracaoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtVerificador verificador;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void cadastroLoginContaETrocaDeSenha() throws Exception {
        String email = emailUnico();
        UUID id = UUID.fromString(criarUsuario(email.toUpperCase(), "senha-forte-1", "NUTRICIONISTA")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        String token = login(email, "senha-forte-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.papeis[0]").value("NUTRICIONISTA"))
                .andReturn().getResponse().getContentAsString();
        String accessToken = json.readTree(token).get("accessToken").asText();

        TokenAcesso acesso = verificador.verificar("Bearer " + accessToken);
        assertThat(acesso.usuarioId()).isEqualTo(id);
        assertThat(acesso.papeis()).containsExactly(Papel.NUTRICIONISTA);

        mvc.perform(get("/v1/conta").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.ultimoAcesso").isNotEmpty());

        mvc.perform(put("/v1/conta/senha").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"senha-forte-1\",\"novaSenha\":\"senha-forte-2\"}"))
                .andExpect(status().isNoContent());
        login(email, "senha-forte-1").andExpect(status().isUnauthorized());
        login(email, "senha-forte-2").andExpect(status().isOk());
    }

    @Test
    void emailDuplicadoIgnorandoMaiusculasResponde409() throws Exception {
        String email = emailUnico();
        criarUsuario(email, "senha-forte-1", "NUTRICIONISTA").andExpect(status().isCreated());
        criarUsuario(email.toUpperCase(), "senha-forte-1", "NUTRICIONISTA")
                .andExpect(status().isConflict());
    }

    @Test
    void adminNuncaECriadoPorApi() throws Exception {
        criarUsuario(emailUnico(), "senha-forte-1", "ADMIN").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void senhaErradaResponde401EBloqueiaAposOLimite() throws Exception {
        String email = emailUnico();
        criarUsuario(email, "senha-forte-1", "NUTRICIONISTA").andExpect(status().isCreated());

        for (int i = 0; i < 3; i++)
            login(email, "errada").andExpect(status().isUnauthorized());
        login(email, "senha-forte-1").andExpect(status().isTooManyRequests());
    }

    @Test
    void emailInexistenteRespondeIgualASenhaErrada() throws Exception {
        login(emailUnico(), "qualquer-senha")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void rotaInternaExigeTokenInternoENaoAceitaJwtDeUsuario() throws Exception {
        String corpo = corpoUsuario(emailUnico(), "senha-forte-1", "NUTRICIONISTA");

        mvc.perform(post("/internal/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .header("Authorization", TokensTeste.nutricionista(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void contaExigeAutenticacao() throws Exception {
        mvc.perform(get("/v1/conta"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .startsWith("application/problem+json"));
    }

    @Test
    void loginsComSucessoEFalhaEntramNaTrilhaSemOEmail() throws Exception {
        String email = emailUnico();
        UUID id = UUID.fromString(json.readTree(criarUsuario(email, "senha-forte-1", "NUTRICIONISTA")
                .andReturn().getResponse().getContentAsString()).get("id").asText());

        login(email, "senha-errada").andExpect(status().isUnauthorized());
        login(email, "senha-forte-1").andExpect(status().isOk());

        var logins = jdbc.queryForList("""
                SELECT payload->>'status' AS status, payload->>'usuarioId' AS usuario, payload::text AS payload
                FROM outbox_evento
                WHERE routing_key = 'auditoria.registro' AND payload->>'acao' = 'LOGIN'
                  AND (payload->>'usuarioId' = ? OR payload->>'usuarioId' IS NULL)
                ORDER BY criado_em DESC LIMIT 2""", id.toString());
        assertThat(logins).extracting(l -> l.get("status")).containsExactly("200", "401");
        assertThat(logins.get(0).get("usuario")).isEqualTo(id.toString());
        assertThat(logins.get(1).get("usuario")).as("falha não identifica a conta").isNull();
        assertThat(logins).allSatisfy(l -> assertThat(l.get("payload").toString()).doesNotContain(email));
    }

    private ResultActions criarUsuario(String email, String senha, String perfil) throws Exception {
        return mvc.perform(post("/internal/usuarios")
                .header(InternalHeaders.INTERNAL_TOKEN, ChavesTeste.TOKEN_INTERNO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoUsuario(email, senha, perfil)));
    }

    private String corpoUsuario(String email, String senha, String perfil) throws Exception {
        JsonNode corpo = json.createObjectNode()
                .put("email", email)
                .put("senha", senha)
                .put("perfil", perfil)
                .set("aceites", json.createArrayNode().add(json.createObjectNode()
                        .put("tipo", "TERMOS_DE_USO").put("versao", "1.0")));
        return json.writeValueAsString(corpo);
    }

    private ResultActions login(String email, String senha) throws Exception {
        return mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("email", email).put("senha", senha))));
    }

    private static String emailUnico() {
        return "nutri-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.local";
    }
}
