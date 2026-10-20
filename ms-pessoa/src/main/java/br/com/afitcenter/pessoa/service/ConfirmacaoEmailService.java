package br.com.afitcenter.pessoa.service;

import br.com.afitcenter.core.auditoria.Acoes;
import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.seguranca.LimiteTentativas;
import br.com.afitcenter.pessoa.email.EnvioEmail;
import br.com.afitcenter.pessoa.email.ModelosEmail;
import br.com.afitcenter.pessoa.model.dto.Dtos.ConfirmarEmailRequest;
import br.com.afitcenter.pessoa.model.entity.Pessoa;
import br.com.afitcenter.pessoa.model.enums.FinalidadeToken;
import br.com.afitcenter.pessoa.repository.PessoaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Confirmação do e-mail. Toda conta nova (cadastro do paciente ou do nutricionista) recebe um link que vale
 * {@code afitcenter.email.confirmacao.validade-horas} (48 por padrão); quem está logado pode pedir outro.
 *
 * <p>O login não exige o e-mail confirmado (v1): {@code GET /v1/conta} mostra {@code emailConfirmado} para o
 * frontend lembrar a pessoa. Redefinir a senha pelo link do e-mail também confirma.</p>
 */
@Service
public class ConfirmacaoEmailService {

    private static final String LINK_INVALIDO =
            "Link de confirmação inválido, já usado ou vencido. Peça um novo na sua conta.";

    private final PessoaRepository repository;
    private final TokensEmail tokens;
    private final ModelosEmail modelos;
    private final EnvioEmail envio;
    private final LimiteTentativas enviosEmail;
    private final Duration validade;

    public ConfirmacaoEmailService(PessoaRepository repository, TokensEmail tokens, ModelosEmail modelos,
                                   EnvioEmail envio, LimiteTentativas enviosEmail,
                                   @Value("${afitcenter.email.confirmacao.validade-horas:48}") long validadeHoras) {
        this.repository = repository;
        this.tokens = tokens;
        this.modelos = modelos;
        this.envio = envio;
        this.enviosEmail = enviosEmail;
        this.validade = Duration.ofHours(validadeHoras);
    }

    /** Gera o link e agenda o e-mail para depois do commit do cadastro. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enviar(Pessoa pessoa) {
        String token = tokens.emitir(pessoa.getId(), FinalidadeToken.CONFIRMACAO_EMAIL, validade);
        envio.enviarAposCommit(modelos.confirmacaoEmail(pessoa, token, validade));
    }

    /** Pública: o token identifica a conta. */
    @Transactional
    public void confirmar(ConfirmarEmailRequest dados) {
        AuditoriaRequisicao.acao(Acoes.ALTERACAO);
        Pessoa pessoa = tokens.consumir(dados.token(), FinalidadeToken.CONFIRMACAO_EMAIL)
                .flatMap(repository::findById)
                .orElseThrow(() -> new RegraNegocioException(LINK_INVALIDO));
        PessoaService.registrarNaTrilha(pessoa, true);
        pessoa.confirmarEmail();
    }

    /** Um link novo invalida o anterior. Conta no mesmo limite de e-mails por endereço do "esqueci a senha". */
    @Transactional
    public void reenviar(UUID pessoaId) {
        Pessoa pessoa = repository.findById(pessoaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pessoa não encontrada."));
        if (pessoa.isEmailConfirmado())
            throw new ConflitoException("O e-mail desta conta já está confirmado.");
        enviosEmail.verificarBloqueio(pessoa.getEmail());
        enviosEmail.registrarFalha(pessoa.getEmail());
        enviar(pessoa);
    }
}
