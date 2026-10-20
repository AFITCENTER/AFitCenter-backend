package br.com.afitcenter.pessoa.service;

import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.base.validacao.Cpf;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.seguranca.LimiteTentativas;
import br.com.afitcenter.pessoa.model.dto.Dtos.AceiteRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.AtualizarContaRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.CadastroPacienteRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.CadastroResponse;
import br.com.afitcenter.pessoa.model.dto.Dtos.ContaResponse;
import br.com.afitcenter.pessoa.model.dto.Dtos.PessoaInternoResponse;
import br.com.afitcenter.pessoa.model.dto.Dtos.PessoaRegistradaResponse;
import br.com.afitcenter.pessoa.model.dto.Dtos.RegistrarPessoaRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.TrocaSenhaRequest;
import br.com.afitcenter.pessoa.model.entity.AceiteTermo;
import br.com.afitcenter.pessoa.model.entity.Pessoa;
import br.com.afitcenter.pessoa.model.enums.TipoTermo;
import br.com.afitcenter.pessoa.repository.AceiteTermoRepository;
import br.com.afitcenter.pessoa.repository.PessoaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Contas de acesso. Uma pessoa por e-mail e por CPF, com um ou mais papéis: quem já tem conta e se cadastra com
 * outro papel (a nutricionista que vira paciente de um colega) usa o mesmo e-mail e senha, e o papel novo é
 * acrescentado à conta.
 */
@Service
@RequiredArgsConstructor
public class PessoaService {

    private final PessoaRepository repository;
    private final AceiteTermoRepository aceiteRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimiteTentativas tentativasSenha;
    private final ConfirmacaoEmailService confirmacaoEmail;

    static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Requisição anônima sobre uma conta (links por e-mail): a trilha registra a conta e, se ela é de nutricionista,
     * o registro entra na trilha dele. {@code autor}: quem fez a requisição provou ser o dono (abriu o link do
     * e-mail); um pedido de "esqueci a senha" pode ter sido feito por qualquer um.
     */
    static void registrarNaTrilha(Pessoa pessoa, boolean autor) {
        if (autor) AuditoriaRequisicao.usuario(pessoa.getId());
        AuditoriaRequisicao.recursoId(pessoa.getId());
        if (pessoa.possui(Papel.NUTRICIONISTA)) AuditoriaRequisicao.nutricionista(pessoa.getId());
    }

    /** Cadastro público do paciente. A ficha é ligada depois, com o código de acesso gerado pelo nutricionista. */
    @Transactional
    public CadastroResponse cadastrarPaciente(CadastroPacienteRequest dados, String ip) {
        var aceites = List.of(
                new AceiteRequest(TipoTermo.TERMOS_DE_USO, dados.versaoTermos()),
                new AceiteRequest(TipoTermo.POLITICA_PRIVACIDADE, dados.versaoTermos()));
        PessoaRegistradaResponse registrada = registrar(new RegistrarPessoaRequest(dados.nome(), dados.cpf(),
                dados.email(), dados.senha(), dados.telefone(), Papel.PACIENTE, aceites, ip));
        // A requisição é anônima: a trilha registra quem acabou de se cadastrar.
        AuditoriaRequisicao.usuario(registrada.id());
        AuditoriaRequisicao.recursoId(registrada.id());
        return new CadastroResponse(registrada.id());
    }

    /**
     * Cria a pessoa com o papel ou, se o e-mail já tem conta e a senha confere, acrescenta o papel a ela.
     * Chamado pelo cadastro do paciente e, pela rota interna, pelo cadastro do nutricionista. ADMIN nunca é criado
     * por API. Conta nova recebe o link de confirmação do e-mail.
     */
    @Transactional
    public PessoaRegistradaResponse registrar(RegistrarPessoaRequest dados) {
        if (dados.papel() == Papel.ADMIN)
            throw new RegraNegocioException("Papel não permitido.");

        String email = normalizarEmail(dados.email());
        String cpf = Cpf.normalizar(dados.cpf());
        Optional<Pessoa> existente = repository.findByEmail(email);
        if (existente.isPresent())
            return acrescentarPapel(existente.get(), dados, cpf);

        if (cpf != null && repository.existsByCpf(cpf))
            throw new ConflitoException("Já existe uma conta com este CPF.");
        Pessoa pessoa = repository.save(new Pessoa(dados.nome().trim(), cpf, email, vazioComoNulo(dados.telefone()),
                passwordEncoder.encode(dados.senha()), dados.papel()));
        registrarAceites(pessoa.getId(), dados);
        confirmacaoEmail.enviar(pessoa);
        return new PessoaRegistradaResponse(pessoa.getId(), true);
    }

    /**
     * Desfaz {@link #registrar}: tira o papel e, se a pessoa ficar sem nenhum, remove a conta (ela foi criada pelo
     * cadastro que falhou). Usado só como compensação do cadastro do nutricionista. Idempotente.
     */
    @Transactional
    public void removerPapel(UUID id, Papel papel) {
        repository.findById(id).ifPresent(pessoa -> {
            pessoa.removerPapel(papel);
            if (pessoa.getPapeis().isEmpty()) {
                aceiteRepository.deleteAll(aceiteRepository.findAllByPessoaId(id));
                repository.delete(pessoa);
            }
        });
    }

    @Transactional(readOnly = true)
    public ContaResponse conta(UUID id) {
        return ContaResponse.de(buscar(id));
    }

    @Transactional
    public ContaResponse atualizarConta(UUID id, AtualizarContaRequest dados) {
        Pessoa pessoa = buscar(id);
        pessoa.atualizarContato(dados.nome().trim(), vazioComoNulo(dados.telefone()));
        return ContaResponse.de(pessoa);
    }

    @Transactional
    public void trocarSenha(UUID id, TrocaSenhaRequest dados) {
        Pessoa pessoa = buscar(id);
        if (!passwordEncoder.matches(dados.senhaAtual(), pessoa.getSenhaHash()))
            throw new RegraNegocioException("A senha atual não confere.");
        pessoa.alterarSenha(passwordEncoder.encode(dados.novaSenha()));
    }

    @Transactional(readOnly = true)
    public PessoaInternoResponse buscarInterno(UUID id) {
        return PessoaInternoResponse.de(buscar(id));
    }

    /**
     * Conta que já existe: quem se cadastra prova que é o dono com a senha dela. A senha errada conta no mesmo
     * limite do login, para o cadastro não virar um atalho para adivinhar senhas.
     */
    private PessoaRegistradaResponse acrescentarPapel(Pessoa pessoa, RegistrarPessoaRequest dados, String cpf) {
        tentativasSenha.verificarBloqueio(pessoa.getEmail());
        if (!passwordEncoder.matches(dados.senha(), pessoa.getSenhaHash())) {
            tentativasSenha.registrarFalha(pessoa.getEmail());
            throw new ConflitoException("Já existe uma conta com este e-mail. Para usá-la, informe a senha dela.");
        }
        tentativasSenha.registrarSucesso(pessoa.getEmail());

        if (pessoa.possui(dados.papel()))
            throw new ConflitoException("Esta conta já está cadastrada como " + descricao(dados.papel()) + ".");
        if (!pessoa.isAtivo())
            throw new RegraNegocioException("Esta conta está inativa ou bloqueada.");
        if (cpf != null && pessoa.getCpf() != null && !pessoa.getCpf().equals(cpf))
            throw new ConflitoException("O CPF informado é diferente do cadastrado nesta conta.");
        if (cpf != null && pessoa.getCpf() == null) {
            if (repository.existsByCpf(cpf))
                throw new ConflitoException("Já existe uma conta com este CPF.");
            pessoa.informarCpf(cpf);
        }

        pessoa.adicionarPapel(dados.papel());
        registrarAceites(pessoa.getId(), dados);
        return new PessoaRegistradaResponse(pessoa.getId(), false);
    }

    /** Aceite que a conta já tinha (mesmo termo e versão) não é duplicado. */
    private void registrarAceites(UUID pessoaId, RegistrarPessoaRequest dados) {
        dados.aceites().stream().distinct()
                .filter(aceite -> !aceiteRepository.existsByPessoaIdAndTipoAndVersao(pessoaId, aceite.tipo(), aceite.versao()))
                .forEach(aceite -> aceiteRepository.save(new AceiteTermo(pessoaId, aceite.tipo(), aceite.versao(), dados.ip())));
    }

    private Pessoa buscar(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Pessoa não encontrada."));
    }

    private static String descricao(Papel papel) {
        return papel.name().toLowerCase(Locale.ROOT);
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
