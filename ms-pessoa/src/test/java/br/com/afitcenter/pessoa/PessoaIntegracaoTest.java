package br.com.afitcenter.pessoa;

import br.com.afitcenter.core.auth.jwt.TokenAcesso;
import br.com.afitcenter.core.base.http.InternalHeaders;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.core.teste.DadosTeste;
import br.com.afitcenter.core.teste.TokensTeste;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PessoaIntegracaoTest extends IntegracaoBase {

    @Test
    void nutricionistaPelaRotaInternaLoginContaETrocaDeSenha() throws Exception {
        String email = DadosTeste.email("nutri");
        String cpf = DadosTeste.cpf();
        UUID id = registrar(nutricionista(email.toUpperCase(), cpf, "senha-forte-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criada").value(true))
                .andReturn().getResponse().getContentAsString().transform(this::id);

        String accessToken = token(login(email, "senha-forte-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.usuarioId").value(id.toString()))
                .andExpect(jsonPath("$.papeis[0]").value("NUTRICIONISTA")));

        TokenAcesso acesso = verificador.verificar("Bearer " + accessToken);
        assertThat(acesso.usuarioId()).isEqualTo(id);
        assertThat(acesso.papeis()).containsExactly(Papel.NUTRICIONISTA);

        mvc.perform(get("/v1/conta").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Teste"))
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailConfirmado").value(false))
                .andExpect(jsonPath("$.papeis[0]").value("NUTRICIONISTA"))
                .andExpect(jsonPath("$.ultimoAcesso").isNotEmpty());

        mvc.perform(put("/v1/conta").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\" Ana Souza \",\"telefone\":\"11911112222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.telefone").value("11911112222"))
                .andExpect(jsonPath("$.cpf").value(cpf));

        mvc.perform(put("/v1/conta/senha").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"senha-forte-1\",\"novaSenha\":\"senha-forte-2\"}"))
                .andExpect(status().isNoContent());
        login(email, "senha-forte-1").andExpect(status().isUnauthorized());
        login(email, "senha-forte-2").andExpect(status().isOk());
    }

    @Test
    void pacienteSeCadastraSozinhoEEntraComOPapelPaciente() throws Exception {
        String email = DadosTeste.email("paciente");
        String cpf = DadosTeste.cpf();
        UUID id = cadastrarPaciente(paciente(email, cpf, "senha-forte-1"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()
                .transform(corpo -> UUID.fromString(ler(corpo, "pessoaId")));

        String accessToken = token(login(email, "senha-forte-1").andExpect(status().isOk()));
        assertThat(verificador.verificar("Bearer " + accessToken).papeis()).containsExactly(Papel.PACIENTE);
        mvc.perform(get("/v1/conta").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.cpf").value(cpf));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM aceite_termo WHERE pessoa_id = ?", Long.class, id))
                .as("termos de uso e política de privacidade").isEqualTo(2);
    }

    @Test
    void cpfUnicoEValidacoesDoCadastroDoPaciente() throws Exception {
        String cpf = DadosTeste.cpf();
        cadastrarPaciente(paciente(DadosTeste.email("a"), cpf, "senha-forte-1")).andExpect(status().isCreated());
        cadastrarPaciente(paciente(DadosTeste.email("b"), cpf, "senha-forte-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma conta com este CPF."));

        // CPF é opcional para o paciente (ex.: menor de idade).
        cadastrarPaciente(paciente(DadosTeste.email("c"), null, "senha-forte-1")).andExpect(status().isCreated());

        ObjectNode semAceite = paciente(DadosTeste.email("d"), "12345678900", "curta");
        semAceite.put("aceiteTermos", false);
        cadastrarPaciente(semAceite)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.cpf").value("CPF inválido."))
                .andExpect(jsonPath("$.erros.senha").exists())
                .andExpect(jsonPath("$.erros.aceiteTermos").exists());
    }

    @Test
    void contaQueJaExisteGanhaOPapelNovoComAMesmaSenha() throws Exception {
        String email = DadosTeste.email("ana");
        String cpf = DadosTeste.cpf();
        UUID id = registrar(nutricionista(email, cpf, "senha-forte-1")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::id);

        cadastrarPaciente(paciente(email, cpf, "senha-errada"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma conta com este e-mail. Para usá-la, informe a senha dela."));
        cadastrarPaciente(paciente(email.toUpperCase(), DadosTeste.cpf(), "senha-forte-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O CPF informado é diferente do cadastrado nesta conta."));

        cadastrarPaciente(paciente(email, cpf, "senha-forte-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pessoaId").value(id.toString()));
        cadastrarPaciente(paciente(email, null, "senha-forte-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta conta já está cadastrada como paciente."));

        String accessToken = token(login(email, "senha-forte-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis[0]").value("NUTRICIONISTA"))
                .andExpect(jsonPath("$.papeis[1]").value("PACIENTE")));
        assertThat(verificador.verificar("Bearer " + accessToken).papeis())
                .containsExactlyInAnyOrder(Papel.NUTRICIONISTA, Papel.PACIENTE);
        mvc.perform(get("/v1/conta").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.nome").value("Ana Teste"));
    }

    @Test
    void pacienteSemCpfQueViraNutricionistaCompletaOCpf() throws Exception {
        String email = DadosTeste.email("bia");
        String cpf = DadosTeste.cpf();
        UUID id = cadastrarPaciente(paciente(email, null, "senha-forte-1")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()
                .transform(corpo -> UUID.fromString(ler(corpo, "pessoaId")));

        registrar(nutricionista(email, cpf, "senha-forte-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.criada").value(false));

        assertThat(jdbc.queryForObject("SELECT cpf FROM pessoa WHERE id = ?", String.class, id)).isEqualTo(cpf);
        registrar(nutricionista(email, cpf, "senha-forte-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta conta já está cadastrada como nutricionista."));
    }

    @Test
    void compensacaoTiraOPapelERemoveAContaQueFicaSemNenhum() throws Exception {
        UUID nova = registrar(nutricionista(DadosTeste.email("nova"), DadosTeste.cpf(), "senha-forte-1"))
                .andReturn().getResponse().getContentAsString().transform(this::id);
        removerPapel(nova, "NUTRICIONISTA").andExpect(status().isNoContent());
        removerPapel(nova, "NUTRICIONISTA").andExpect(status().isNoContent()); // idempotente
        buscarInterno(nova).andExpect(status().isNotFound());

        String email = DadosTeste.email("dupla");
        UUID existente = cadastrarPaciente(paciente(email, null, "senha-forte-1"))
                .andReturn().getResponse().getContentAsString()
                .transform(corpo -> UUID.fromString(ler(corpo, "pessoaId")));
        registrar(nutricionista(email, DadosTeste.cpf(), "senha-forte-1")).andExpect(status().isCreated());
        removerPapel(existente, "NUTRICIONISTA").andExpect(status().isNoContent());

        buscarInterno(existente).andExpect(status().isOk());
        login(email, "senha-forte-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis.length()").value(1))
                .andExpect(jsonPath("$.papeis[0]").value("PACIENTE"));
    }

    @Test
    void pagadorDaAssinaturaPelaRotaInterna() throws Exception {
        String email = DadosTeste.email("pagadora");
        String cpf = DadosTeste.cpf();
        UUID id = registrar(nutricionista(email, cpf, "senha-forte-1"))
                .andReturn().getResponse().getContentAsString().transform(this::id);

        buscarInterno(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Teste"))
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
        buscarInterno(UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void adminNuncaECriadoPorApi() throws Exception {
        ObjectNode corpo = nutricionista(DadosTeste.email("adm"), null, "senha-forte-1");
        corpo.put("papel", "ADMIN");
        registrar(corpo).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void senhaErradaResponde401EBloqueiaAposOLimite() throws Exception {
        String email = DadosTeste.email("nutri");
        registrar(nutricionista(email, DadosTeste.cpf(), "senha-forte-1")).andExpect(status().isCreated());

        for (int i = 0; i < 3; i++)
            login(email, "errada").andExpect(status().isUnauthorized());
        login(email, "senha-forte-1").andExpect(status().isTooManyRequests());
    }

    @Test
    void cadastroComSenhaErradaContaNoMesmoLimiteDoLogin() throws Exception {
        String email = DadosTeste.email("alvo");
        registrar(nutricionista(email, DadosTeste.cpf(), "senha-forte-1")).andExpect(status().isCreated());

        for (int i = 0; i < 3; i++)
            cadastrarPaciente(paciente(email, null, "chute-errado-" + i)).andExpect(status().isConflict());
        cadastrarPaciente(paciente(email, null, "senha-forte-1")).andExpect(status().isTooManyRequests());
        login(email, "senha-forte-1").andExpect(status().isTooManyRequests());
    }

    @Test
    void emailInexistenteRespondeIgualASenhaErrada() throws Exception {
        login(DadosTeste.email("ninguem"), "qualquer-senha")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void rotasInternasExigemTokenInternoENaoAceitamJwtDeUsuario() throws Exception {
        String corpo = json.writeValueAsString(nutricionista(DadosTeste.email("x"), null, "senha-forte-1"));

        mvc.perform(post("/internal/pessoas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/pessoas").contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .header("Authorization", TokensTeste.nutricionista(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/pessoas/" + UUID.randomUUID())
                        .header("Authorization", TokensTeste.paciente(UUID.randomUUID())))
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
    void loginsECadastroEntramNaTrilhaSemOEmail() throws Exception {
        String email = DadosTeste.email("trilha");
        UUID id = cadastrarPaciente(paciente(email, null, "senha-forte-1"))
                .andReturn().getResponse().getContentAsString()
                .transform(corpo -> UUID.fromString(ler(corpo, "pessoaId")));

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

        String cadastro = jdbc.queryForObject("""
                SELECT payload::text FROM outbox_evento
                WHERE routing_key = 'auditoria.registro' AND payload->>'rota' = '/v1/cadastros/paciente'
                  AND payload->>'usuarioId' = ?""", String.class, id.toString());
        assertThat(json.readTree(cadastro).get("papel").asText()).isEqualTo("ANONIMO");
        assertThat(cadastro).doesNotContain(email).doesNotContain("senha-forte-1");
    }

    // ------------------------------------------------------------------ auxiliares

    private ResultActions removerPapel(UUID id, String papel) throws Exception {
        return mvc.perform(delete("/internal/pessoas/" + id + "/papeis/" + papel)
                .header(InternalHeaders.INTERNAL_TOKEN, ChavesTeste.TOKEN_INTERNO));
    }

    private ResultActions buscarInterno(UUID id) throws Exception {
        return mvc.perform(get("/internal/pessoas/" + id).header(InternalHeaders.INTERNAL_TOKEN, ChavesTeste.TOKEN_INTERNO));
    }
}
