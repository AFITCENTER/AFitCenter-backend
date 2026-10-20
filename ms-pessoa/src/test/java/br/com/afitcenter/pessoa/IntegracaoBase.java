package br.com.afitcenter.pessoa;

import br.com.afitcenter.core.auth.jwt.JwtVerificador;
import br.com.afitcenter.core.base.http.InternalHeaders;
import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.pessoa.email.MensagemEmail;
import br.com.afitcenter.pessoa.email.ProvedorEmail;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base dos testes do ms-pessoa: mesmo contexto para todas as classes. Os e-mails passam pelo provedor {@code LOG}
 * de verdade, observado por um spy: os testes leem o link do e-mail que foi "enviado".
 */
@TesteIntegracao
@TestPropertySource(properties = {
        "afitcenter.jwt.chave-privada=" + ChavesTeste.PRIVADA,
        "afitcenter.login.max-falhas=3",
        "afitcenter.email.provedor=LOG",
        "afitcenter.email.max-envios=3"
})
abstract class IntegracaoBase {

    protected static final String ASSUNTO_REDEFINICAO = "AFitCenter: redefinição de senha";
    protected static final String ASSUNTO_CONFIRMACAO = "AFitCenter: confirme seu e-mail";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JwtVerificador verificador;
    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoSpyBean
    protected ProvedorEmail provedorEmail;

    // ------------------------------------------------------------------ corpos

    protected ObjectNode nutricionista(String email, String cpf, String senha) {
        ObjectNode corpo = json.createObjectNode()
                .put("nome", "Ana Teste")
                .put("email", email)
                .put("senha", senha)
                .put("telefone", "11900001111")
                .put("papel", "NUTRICIONISTA");
        if (cpf != null) corpo.put("cpf", cpf);
        corpo.set("aceites", json.createArrayNode().add(json.createObjectNode()
                .put("tipo", "TERMOS_DE_USO").put("versao", "1.0")));
        return corpo;
    }

    protected ObjectNode paciente(String email, String cpf, String senha) {
        ObjectNode corpo = json.createObjectNode()
                .put("nome", "Carlos Teste")
                .put("email", email)
                .put("senha", senha)
                .put("aceiteTermos", true)
                .put("versaoTermos", "1.0");
        if (cpf != null) corpo.put("cpf", cpf);
        return corpo;
    }

    // ------------------------------------------------------------------ chamadas

    protected ResultActions registrar(ObjectNode corpo) throws Exception {
        return mvc.perform(post("/internal/pessoas")
                .header(InternalHeaders.INTERNAL_TOKEN, ChavesTeste.TOKEN_INTERNO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)));
    }

    protected ResultActions cadastrarPaciente(ObjectNode corpo) throws Exception {
        return mvc.perform(post("/v1/cadastros/paciente").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)));
    }

    /** Cadastra um paciente sem CPF e devolve o id da pessoa. */
    protected UUID novoPaciente(String email, String senha) throws Exception {
        return UUID.fromString(ler(cadastrarPaciente(paciente(email, null, senha))
                .andReturn().getResponse().getContentAsString(), "pessoaId"));
    }

    protected ResultActions login(String email, String senha) throws Exception {
        return mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("email", email).put("senha", senha))));
    }

    protected String token(ResultActions login) throws Exception {
        return ler(login.andReturn().getResponse().getContentAsString(), "accessToken");
    }

    protected UUID id(String corpo) {
        return UUID.fromString(ler(corpo, "id"));
    }

    protected String ler(String corpo, String campo) {
        try {
            return json.readTree(corpo).get(campo).asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ e-mails

    /** E-mails com o assunto já entregues ao provedor para o endereço, na ordem de entrega. */
    protected List<MensagemEmail> emails(String para, String assunto) {
        return Mockito.mockingDetails(provedorEmail).getInvocations().stream()
                .filter(invocacao -> invocacao.getMethod().getName().equals("enviar"))
                .map(invocacao -> (MensagemEmail) invocacao.getArgument(0))
                .filter(email -> email.para().equals(para) && email.assunto().equals(assunto))
                .toList();
    }

    /** Espera o {@code n}-ésimo e-mail (1 = o primeiro) com o assunto: o envio é assíncrono, depois do commit. */
    protected MensagemEmail aguardarEmail(String para, String assunto, int n) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        List<MensagemEmail> recebidos;
        while ((recebidos = emails(para, assunto)).size() < n) {
            if (System.nanoTime() > limite)
                throw new AssertionError("O e-mail '" + assunto + "' nº " + n + " não chegou; chegaram " + recebidos.size() + ".");
            Thread.sleep(20);
        }
        return recebidos.get(n - 1);
    }
}
