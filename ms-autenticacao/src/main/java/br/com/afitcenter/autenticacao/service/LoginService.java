package br.com.afitcenter.autenticacao.service;

import br.com.afitcenter.autenticacao.model.dto.Dtos.LoginRequest;
import br.com.afitcenter.autenticacao.model.dto.Dtos.TokenResponse;
import br.com.afitcenter.autenticacao.model.entity.Usuario;
import br.com.afitcenter.autenticacao.repository.UsuarioRepository;
import br.com.afitcenter.core.auditoria.Acoes;
import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.auth.jwt.JwtEmissor;
import br.com.afitcenter.core.base.security.Papel;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class LoginService {

    private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEmissor jwtEmissor;
    private final TentativasLoginService tentativas;
    /** Comparado quando o e-mail não existe, para o tempo de resposta não revelar quais e-mails existem. */
    private final String hashFicticio;

    public LoginService(UsuarioRepository repository, PasswordEncoder passwordEncoder, JwtEmissor jwtEmissor,
                        TentativasLoginService tentativas) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEmissor = jwtEmissor;
        this.tentativas = tentativas;
        this.hashFicticio = passwordEncoder.encode("senha-ficticia-para-tempo-constante");
    }

    /** Sucesso e falha vão para a trilha de auditoria (ação LOGIN); o e-mail nunca é registrado. */
    @Transactional
    public TokenResponse autenticar(LoginRequest dados) {
        AuditoriaRequisicao.acao(Acoes.LOGIN);
        String email = UsuarioService.normalizarEmail(dados.email());
        tentativas.verificarBloqueio(email);

        Optional<Usuario> encontrado = repository.findByEmail(email);
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        if (!passwordEncoder.matches(dados.senha(), hash) || encontrado.isEmpty()) {
            tentativas.registrarFalha(email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIAIS_INVALIDAS);
        }

        Usuario usuario = encontrado.get();
        AuditoriaRequisicao.usuario(usuario.getId());
        AuditoriaRequisicao.recursoId(usuario.getId());
        if (usuario.getPerfil() == Papel.NUTRICIONISTA) AuditoriaRequisicao.nutricionista(usuario.getId());
        if (!usuario.isAtivo())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário inativo ou bloqueado.");

        tentativas.registrarSucesso(email);
        usuario.registrarAcesso();

        String token = jwtEmissor.emitir(usuario.getId(), Set.of(usuario.getPerfil()));
        return new TokenResponse(token, "Bearer", jwtEmissor.validade().toSeconds(), usuario.getId(),
                List.of(usuario.getPerfil().name()));
    }
}
