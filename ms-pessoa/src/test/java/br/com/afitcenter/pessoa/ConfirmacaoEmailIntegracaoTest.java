package br.com.afitcenter.pessoa;

import br.com.afitcenter.core.teste.DadosTeste;
import br.com.afitcenter.pessoa.email.MensagemEmail;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfirmacaoEmailIntegracaoTest extends IntegracaoBase {

    private static final String LINK_INVALIDO = "Link de confirmação inválido, já usado ou vencido. Peça um novo na sua conta.";

    @Test
    void pacienteRecebeOLinkNoCadastroEConfirmaSemEstarLogado() throws Exception {
        String email = DadosTeste.email("confirma");
        novoPaciente(email, "senha-forte-1");
        MensagemEmail mensagem = aguardarEmail(email, ASSUNTO_CONFIRMACAO, 1);
        assertThat(mensagem.texto()).contains("http://localhost:5173/confirmar-email#token=" + mensagem.segredo())
                .contains("48 horas");

        // O login não exige o e-mail confirmado.
        String accessToken = token(login(email, "senha-forte-1").andExpect(status().isOk()));
        conta(accessToken).andExpect(jsonPath("$.emailConfirmado").value(false));

        confirmar(mensagem.segredo()).andExpect(status().isNoContent());
        conta(accessToken).andExpect(jsonPath("$.emailConfirmado").value(true));

        confirmar(mensagem.segredo())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(LINK_INVALIDO));
        reenviar(accessToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O e-mail desta conta já está confirmado."));
    }

    @Test
    void nutricionistaRecebeOLinkEPodePedirOutro() throws Exception {
        String email = DadosTeste.email("nutri-confirma");
        registrar(nutricionista(email, DadosTeste.cpf(), "senha-forte-1")).andExpect(status().isCreated());
        String primeiro = aguardarEmail(email, ASSUNTO_CONFIRMACAO, 1).segredo();
        String accessToken = token(login(email, "senha-forte-1"));

        mvc.perform(post("/v1/conta/email/confirmacao/reenvio")).andExpect(status().isUnauthorized());
        reenviar(accessToken).andExpect(status().isAccepted());
        String segundo = aguardarEmail(email, ASSUNTO_CONFIRMACAO, 2).segredo();

        confirmar(primeiro).andExpect(status().isUnprocessableEntity());
        confirmar(segundo).andExpect(status().isNoContent());
        conta(accessToken).andExpect(jsonPath("$.emailConfirmado").value(true));
    }

    @Test
    void linkVencidoOuDeOutraFinalidadeNaoConfirma() throws Exception {
        String email = DadosTeste.email("confirma-vencido");
        UUID id = novoPaciente(email, "senha-forte-1");
        String token = aguardarEmail(email, ASSUNTO_CONFIRMACAO, 1).segredo();

        jdbc.update("UPDATE token_email SET expira_em = now() - interval '1 second' "
                + "WHERE pessoa_id = ? AND finalidade = 'CONFIRMACAO_EMAIL'", id);
        confirmar(token).andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/v1/senha/esqueci").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(json.createObjectNode().put("email", email))))
                .andExpect(status().isAccepted());
        confirmar(aguardarEmail(email, ASSUNTO_REDEFINICAO, 1).segredo()).andExpect(status().isUnprocessableEntity());

        assertThat(jdbc.queryForObject("SELECT email_confirmado_em FROM pessoa WHERE id = ?", Object.class, id)).isNull();
    }

    @Test
    void contaQueJaExistiaGanhaOPapelSemLinkNovo() throws Exception {
        String email = DadosTeste.email("ja-existia");
        UUID id = novoPaciente(email, "senha-forte-1");
        String hashAntes = hashConfirmacao(id);

        registrar(nutricionista(email, DadosTeste.cpf(), "senha-forte-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.criada").value(false));
        assertThat(hashConfirmacao(id)).isEqualTo(hashAntes);
    }

    @Test
    void reenvioContaNoMesmoLimiteDoEsqueciASenha() throws Exception {
        String email = DadosTeste.email("reenvios");
        novoPaciente(email, "senha-forte-1");
        String accessToken = token(login(email, "senha-forte-1"));

        for (int i = 0; i < 3; i++)
            reenviar(accessToken).andExpect(status().isAccepted());
        reenviar(accessToken).andExpect(status().isTooManyRequests());
        mvc.perform(post("/v1/senha/esqueci").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(json.createObjectNode().put("email", email))))
                .andExpect(status().isTooManyRequests());
    }

    private String hashConfirmacao(UUID pessoaId) {
        return jdbc.queryForObject("SELECT token_hash FROM token_email WHERE pessoa_id = ? AND finalidade = 'CONFIRMACAO_EMAIL'",
                String.class, pessoaId);
    }

    private ResultActions confirmar(String token) throws Exception {
        return mvc.perform(post("/v1/conta/email/confirmacao").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("token", token))));
    }

    private ResultActions reenviar(String accessToken) throws Exception {
        return mvc.perform(post("/v1/conta/email/confirmacao/reenvio").header("Authorization", "Bearer " + accessToken));
    }

    private ResultActions conta(String accessToken) throws Exception {
        return mvc.perform(get("/v1/conta").header("Authorization", "Bearer " + accessToken)).andExpect(status().isOk());
    }
}
