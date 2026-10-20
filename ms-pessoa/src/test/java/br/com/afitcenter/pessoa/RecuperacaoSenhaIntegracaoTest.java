package br.com.afitcenter.pessoa;

import br.com.afitcenter.core.teste.DadosTeste;
import br.com.afitcenter.pessoa.email.MensagemEmail;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecuperacaoSenhaIntegracaoTest extends IntegracaoBase {

    private static final String LINK_INVALIDO = "Link de redefinição inválido, já usado ou vencido. Peça um novo.";

    @Test
    void esqueciEmailRedefinirELoginComASenhaNova() throws Exception {
        String email = DadosTeste.email("esqueceu");
        UUID id = registrar(nutricionista(email, DadosTeste.cpf(), "senha-antiga-1")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::id);

        esqueci(email.toUpperCase()).andExpect(status().isAccepted());
        MensagemEmail mensagem = aguardarEmail(email, ASSUNTO_REDEFINICAO, 1);
        String token = mensagem.segredo();
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(mensagem.texto()).contains("http://localhost:5173/redefinir-senha#token=" + token)
                .contains("Ana Teste").contains("30 minutos");

        // O banco guarda só o SHA-256 do token.
        assertThat(jdbc.queryForList("SELECT token_hash FROM token_email WHERE pessoa_id = ? AND finalidade = 'REDEFINICAO_SENHA'",
                String.class, id)).singleElement().satisfies(hash -> assertThat(hash).hasSize(64).isNotEqualTo(token));

        redefinir(token, "senha-nova-123").andExpect(status().isNoContent());
        login(email, "senha-antiga-1").andExpect(status().isUnauthorized());
        String accessToken = token(login(email, "senha-nova-123").andExpect(status().isOk()));

        // Quem abriu o link provou que lê o e-mail.
        mvc.perform(get("/v1/conta").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.emailConfirmado").value(true));

        // Uso único: o mesmo link não troca a senha de novo.
        redefinir(token, "senha-de-quem-reusou")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(LINK_INVALIDO));
        login(email, "senha-nova-123").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_email WHERE pessoa_id = ? AND finalidade = 'REDEFINICAO_SENHA'",
                Long.class, id)).isZero();
    }

    @Test
    void emailSemContaRespondeIgualENaoRecebeNada() throws Exception {
        String comConta = DadosTeste.email("tem-conta");
        String semConta = DadosTeste.email("sem-conta");
        novoPaciente(comConta, "senha-forte-1");

        MockHttpServletResponse respostaComConta = esqueci(comConta).andReturn().getResponse();
        MockHttpServletResponse respostaSemConta = esqueci(semConta).andReturn().getResponse();
        assertThat(respostaSemConta.getStatus()).isEqualTo(respostaComConta.getStatus()).isEqualTo(202);
        assertThat(respostaSemConta.getContentAsString()).isEqualTo(respostaComConta.getContentAsString()).isEmpty();
        assertThat(respostaSemConta.getHeaderNames()).isEqualTo(respostaComConta.getHeaderNames());

        aguardarEmail(comConta, ASSUNTO_REDEFINICAO, 1);
        assertThat(emails(semConta, ASSUNTO_REDEFINICAO)).isEmpty();

        // O limite por endereço conta os dois do mesmo jeito: o 429 também não revela quem tem conta.
        for (String email : new String[]{comConta, semConta}) {
            esqueci(email).andExpect(status().isAccepted());
            esqueci(email).andExpect(status().isAccepted());
            esqueci(email).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.detail").value("Muitos pedidos de e-mail para este endereço. Tente novamente mais tarde."));
        }
    }

    @Test
    void linkVencidoNaoTrocaASenha() throws Exception {
        String email = DadosTeste.email("vencido");
        UUID id = novoPaciente(email, "senha-forte-1");
        esqueci(email).andExpect(status().isAccepted());
        String token = aguardarEmail(email, ASSUNTO_REDEFINICAO, 1).segredo();

        jdbc.update("UPDATE token_email SET expira_em = now() - interval '1 second' "
                + "WHERE pessoa_id = ? AND finalidade = 'REDEFINICAO_SENHA'", id);
        redefinir(token, "senha-nova-123")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(LINK_INVALIDO));
        login(email, "senha-forte-1").andExpect(status().isOk());
        login(email, "senha-nova-123").andExpect(status().isUnauthorized());
    }

    @Test
    void pedirOutroLinkInvalidaOAnteriorETokenDeOutraFinalidadeNaoServe() throws Exception {
        String email = DadosTeste.email("dois-pedidos");
        novoPaciente(email, "senha-forte-1");
        String confirmacao = aguardarEmail(email, ASSUNTO_CONFIRMACAO, 1).segredo();

        esqueci(email).andExpect(status().isAccepted());
        String primeiro = aguardarEmail(email, ASSUNTO_REDEFINICAO, 1).segredo();
        esqueci(email).andExpect(status().isAccepted());
        String segundo = aguardarEmail(email, ASSUNTO_REDEFINICAO, 2).segredo();
        assertThat(segundo).isNotEqualTo(primeiro);

        redefinir(primeiro, "senha-nova-123").andExpect(status().isUnprocessableEntity());
        redefinir(confirmacao, "senha-nova-123").andExpect(status().isUnprocessableEntity());
        redefinir("token-inventado", "senha-nova-123").andExpect(status().isUnprocessableEntity());
        redefinir(segundo, "senha-nova-123").andExpect(status().isNoContent());
        login(email, "senha-nova-123").andExpect(status().isOk());
    }

    @Test
    void redefinirLiberaOLoginBloqueadoPorSenhasErradas() throws Exception {
        String email = DadosTeste.email("bloqueada");
        novoPaciente(email, "senha-forte-1");
        for (int i = 0; i < 3; i++)
            login(email, "chute-" + i).andExpect(status().isUnauthorized());
        login(email, "senha-forte-1").andExpect(status().isTooManyRequests());

        esqueci(email).andExpect(status().isAccepted());
        redefinir(aguardarEmail(email, ASSUNTO_REDEFINICAO, 1).segredo(), "senha-nova-123")
                .andExpect(status().isNoContent());
        login(email, "senha-nova-123").andExpect(status().isOk());
    }

    @Test
    void contaInativaNaoRecebeOLink() throws Exception {
        String email = DadosTeste.email("inativa");
        UUID id = novoPaciente(email, "senha-forte-1");
        aguardarEmail(email, ASSUNTO_CONFIRMACAO, 1);
        jdbc.update("UPDATE pessoa SET status = 'BLOQUEADO' WHERE id = ?", id);

        esqueci(email).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_email WHERE pessoa_id = ? AND finalidade = 'REDEFINICAO_SENHA'",
                Long.class, id)).isZero();
    }

    @Test
    void validacaoNaoDevolveOToken() throws Exception {
        esqueci("nao-e-email").andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.email").exists());

        String corpo = redefinir("um-token-qualquer", "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.novaSenha").value("A senha deve ter entre 8 e 72 caracteres."))
                .andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("um-token-qualquer");
    }

    @Test
    void tokenEmailESenhaNuncaEntramNaTrilha() throws Exception {
        String email = DadosTeste.email("trilha-senha");
        UUID id = registrar(nutricionista(email, DadosTeste.cpf(), "senha-forte-1")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::id);
        String confirmacao = aguardarEmail(email, ASSUNTO_CONFIRMACAO, 1).segredo();

        esqueci(email).andExpect(status().isAccepted());
        String token = aguardarEmail(email, ASSUNTO_REDEFINICAO, 1).segredo();
        redefinir(token, "senha-nova-secreta").andExpect(status().isNoContent());
        redefinir(token, "outra-senha-secreta").andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/conta/email/confirmacao").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(json.createObjectNode().put("token", confirmacao))))
                .andExpect(status().isNoContent());

        var registros = jdbc.queryForList("""
                SELECT payload->>'rota' AS rota, payload->>'status' AS status, payload->>'acao' AS acao,
                       payload->>'usuarioId' AS usuario, payload->>'recursoId' AS recurso,
                       payload->>'nutricionistaId' AS nutricionista
                FROM outbox_evento
                WHERE routing_key = 'auditoria.registro' AND payload->>'recursoId' = ?
                  AND payload->>'rota' IN ('/v1/senha/esqueci', '/v1/senha/redefinir', '/v1/conta/email/confirmacao')
                ORDER BY criado_em""", id.toString());
        assertThat(registros).extracting(r -> r.get("rota") + " " + r.get("status")).containsExactly(
                "/v1/senha/esqueci 202", "/v1/senha/redefinir 204", "/v1/conta/email/confirmacao 204");
        assertThat(registros.get(0).get("usuario")).as("qualquer um pede o link: o pedido não tem autor").isNull();
        assertThat(registros.get(1).get("usuario")).as("quem usou o link é o dono").isEqualTo(id.toString());
        assertThat(registros.get(1).get("acao")).isEqualTo("ALTERACAO");
        assertThat(registros).allSatisfy(r -> assertThat(r.get("nutricionista")).isEqualTo(id.toString()));

        // Nada do corpo, em nenhum registro da trilha.
        for (String segredo : new String[]{token, confirmacao, email, "senha-nova-secreta", "outra-senha-secreta"})
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE strpos(payload::text, ?) > 0",
                    Long.class, segredo)).as(segredo).isZero();
    }

    @Test
    void rotasPublicasNaoExigemToken() throws Exception {
        esqueci(DadosTeste.email("anonimo")).andExpect(status().isAccepted());
        redefinir("inexistente", "senha-nova-123").andExpect(status().isUnprocessableEntity());
    }

    private ResultActions esqueci(String email) throws Exception {
        return mvc.perform(post("/v1/senha/esqueci").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("email", email))));
    }

    private ResultActions redefinir(String token, String novaSenha) throws Exception {
        return mvc.perform(post("/v1/senha/redefinir").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("token", token).put("novaSenha", novaSenha))));
    }
}
