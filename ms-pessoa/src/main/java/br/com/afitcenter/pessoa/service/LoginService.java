package br.com.afitcenter.pessoa.service;

import br.com.afitcenter.core.auditoria.Acoes;
import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.auth.jwt.JwtEmissor;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.web.seguranca.LimiteTentativas;
import br.com.afitcenter.pessoa.model.dto.Dtos.LoginRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.TokenResponse;
import br.com.afitcenter.pessoa.model.entity.Pessoa;
import br.com.afitcenter.pessoa.repository.PessoaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Login de qualquer pessoa: o token leva todos os papéis dela (nutricionista, paciente, administrador). */
@Service
public class LoginService {

    private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

    private final PessoaRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEmissor jwtEmissor;
    private final LimiteTentativas tentativasSenha;
    /** Comparado quando o e-mail não existe, para o tempo de resposta não revelar quais e-mails existem. */
    private final String hashFicticio;

    public LoginService(PessoaRepository repository, PasswordEncoder passwordEncoder, JwtEmissor jwtEmissor,
                        LimiteTentativas tentativasSenha) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEmissor = jwtEmissor;
        this.tentativasSenha = tentativasSenha;
        this.hashFicticio = passwordEncoder.encode("senha-ficticia-para-tempo-constante");
    }

    /** Sucesso e falha vão para a trilha de auditoria (ação LOGIN); o e-mail nunca é registrado. */
    @Transactional
    public TokenResponse autenticar(LoginRequest dados) {
        AuditoriaRequisicao.acao(Acoes.LOGIN);
        String email = PessoaService.normalizarEmail(dados.email());
        tentativasSenha.verificarBloqueio(email);

        Optional<Pessoa> encontrada = repository.findByEmail(email);
        String hash = encontrada.map(Pessoa::getSenhaHash).orElse(hashFicticio);
        if (!passwordEncoder.matches(dados.senha(), hash) || encontrada.isEmpty()) {
            tentativasSenha.registrarFalha(email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIAIS_INVALIDAS);
        }

        Pessoa pessoa = encontrada.get();
        AuditoriaRequisicao.usuario(pessoa.getId());
        AuditoriaRequisicao.recursoId(pessoa.getId());
        if (pessoa.possui(Papel.NUTRICIONISTA)) AuditoriaRequisicao.nutricionista(pessoa.getId());
        if (!pessoa.isAtivo())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta inativa ou bloqueada.");

        tentativasSenha.registrarSucesso(email);
        pessoa.registrarAcesso();

        String token = jwtEmissor.emitir(pessoa.getId(), pessoa.getPapeis());
        return new TokenResponse(token, "Bearer", jwtEmissor.validade().toSeconds(), pessoa.getId(),
                Papel.nomes(pessoa.getPapeis()));
    }
}
