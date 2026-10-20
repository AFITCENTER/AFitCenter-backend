package br.com.afitcenter.pessoa.service;

import br.com.afitcenter.core.auditoria.Acoes;
import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.seguranca.LimiteTentativas;
import br.com.afitcenter.pessoa.email.EnvioEmail;
import br.com.afitcenter.pessoa.email.ModelosEmail;
import br.com.afitcenter.pessoa.model.dto.Dtos.EsqueciSenhaRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.RedefinirSenhaRequest;
import br.com.afitcenter.pessoa.model.entity.Pessoa;
import br.com.afitcenter.pessoa.model.enums.FinalidadeToken;
import br.com.afitcenter.pessoa.repository.PessoaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * "Esqueci a senha": um link de uso único vai para o e-mail da conta e vale
 * {@code afitcenter.senha.redefinicao.validade-minutos} (30 por padrão). Pedir outro invalida o anterior.
 *
 * <p>O pedido responde igual exista ou não a conta, e o e-mail só sai depois da resposta ({@link EnvioEmail}).</p>
 */
@Service
public class RecuperacaoSenhaService {

    private static final String LINK_INVALIDO = "Link de redefinição inválido, já usado ou vencido. Peça um novo.";

    private final PessoaRepository repository;
    private final TokensEmail tokens;
    private final ModelosEmail modelos;
    private final EnvioEmail envio;
    private final PasswordEncoder passwordEncoder;
    private final LimiteTentativas enviosEmail;
    private final LimiteTentativas tentativasSenha;
    private final Duration validade;

    public RecuperacaoSenhaService(PessoaRepository repository, TokensEmail tokens, ModelosEmail modelos,
                                   EnvioEmail envio, PasswordEncoder passwordEncoder, LimiteTentativas enviosEmail,
                                   LimiteTentativas tentativasSenha,
                                   @Value("${afitcenter.senha.redefinicao.validade-minutos:30}") long validadeMinutos) {
        this.repository = repository;
        this.tokens = tokens;
        this.modelos = modelos;
        this.envio = envio;
        this.passwordEncoder = passwordEncoder;
        this.enviosEmail = enviosEmail;
        this.tentativasSenha = tentativasSenha;
        this.validade = Duration.ofMinutes(validadeMinutos);
    }

    /**
     * Conta ativa recebe o link; e-mail sem conta (ou de conta inativa) não recebe nada. Todo pedido conta no limite
     * de e-mails por endereço, exista ou não a conta.
     */
    @Transactional
    public void solicitar(EsqueciSenhaRequest dados) {
        String email = PessoaService.normalizarEmail(dados.email());
        enviosEmail.verificarBloqueio(email);
        enviosEmail.registrarFalha(email);

        repository.findByEmail(email).filter(Pessoa::isAtivo).ifPresent(pessoa -> {
            PessoaService.registrarNaTrilha(pessoa, false);
            String token = tokens.emitir(pessoa.getId(), FinalidadeToken.REDEFINICAO_SENHA, validade);
            envio.enviarAposCommit(modelos.redefinicaoSenha(pessoa, token, validade));
        });
    }

    /**
     * Troca a senha com o token do link. Quem abriu o link provou que lê o e-mail: o e-mail fica confirmado e o
     * bloqueio por senhas erradas é liberado. JWTs já emitidos continuam valendo até expirar.
     */
    @Transactional
    public void redefinir(RedefinirSenhaRequest dados) {
        AuditoriaRequisicao.acao(Acoes.ALTERACAO);
        Pessoa pessoa = tokens.consumir(dados.token(), FinalidadeToken.REDEFINICAO_SENHA)
                .flatMap(repository::findById)
                .orElseThrow(() -> new RegraNegocioException(LINK_INVALIDO));
        PessoaService.registrarNaTrilha(pessoa, true);
        if (!pessoa.isAtivo())
            throw new RegraNegocioException("Esta conta está inativa ou bloqueada.");

        pessoa.alterarSenha(passwordEncoder.encode(dados.novaSenha()));
        pessoa.confirmarEmail();
        tentativasSenha.registrarSucesso(pessoa.getEmail());
    }
}
