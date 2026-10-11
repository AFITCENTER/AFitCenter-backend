package br.com.afitcenter.autenticacao.service;

import br.com.afitcenter.autenticacao.model.dto.Dtos.ContaResponse;
import br.com.afitcenter.autenticacao.model.dto.Dtos.CriarUsuarioRequest;
import br.com.afitcenter.autenticacao.model.dto.Dtos.TrocaSenhaRequest;
import br.com.afitcenter.autenticacao.model.entity.AceiteTermo;
import br.com.afitcenter.autenticacao.model.entity.Usuario;
import br.com.afitcenter.autenticacao.repository.AceiteTermoRepository;
import br.com.afitcenter.autenticacao.repository.UsuarioRepository;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private final AceiteTermoRepository aceiteRepository;
    private final PasswordEncoder passwordEncoder;

    static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Cria a credencial. Chamado pelo ms-nutricionista durante o cadastro (rota interna).
     * ADMIN nunca é criado por API.
     */
    @Transactional
    public UUID criar(CriarUsuarioRequest dados) {
        if (dados.perfil() == Papel.ADMIN)
            throw new RegraNegocioException("Perfil não permitido.");

        String email = normalizarEmail(dados.email());
        if (repository.existsByEmail(email))
            throw new ConflitoException("Já existe uma conta com este e-mail.");

        Usuario usuario = repository.save(new Usuario(email, passwordEncoder.encode(dados.senha()), dados.perfil()));
        dados.aceites().forEach(aceite -> aceiteRepository.save(
                new AceiteTermo(usuario.getId(), aceite.tipo(), aceite.versao(), dados.ip())));
        return usuario.getId();
    }

    /**
     * Remove uma credencial recém-criada. Usado só como compensação, quando o cadastro falhou
     * depois de criar o usuário. Idempotente.
     */
    @Transactional
    public void remover(UUID id) {
        repository.findById(id).ifPresent(usuario -> {
            aceiteRepository.deleteAll(aceiteRepository.findAllByUsuarioId(id));
            repository.delete(usuario);
        });
    }

    @Transactional(readOnly = true)
    public ContaResponse conta(UUID usuarioId) {
        return ContaResponse.de(buscar(usuarioId));
    }

    @Transactional
    public void trocarSenha(UUID usuarioId, TrocaSenhaRequest dados) {
        Usuario usuario = buscar(usuarioId);
        if (!passwordEncoder.matches(dados.senhaAtual(), usuario.getSenhaHash()))
            throw new RegraNegocioException("A senha atual não confere.");
        usuario.alterarSenha(passwordEncoder.encode(dados.novaSenha()));
    }

    private Usuario buscar(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }
}
