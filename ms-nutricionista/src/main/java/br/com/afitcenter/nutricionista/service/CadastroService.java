package br.com.afitcenter.nutricionista.service;

import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.base.validacao.Cpf;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.nutricionista.client.AutenticacaoClient;
import br.com.afitcenter.nutricionista.model.dto.Dtos.AceiteRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.CadastroRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.CadastroResponse;
import br.com.afitcenter.nutricionista.model.dto.Dtos.CriarUsuarioRequest;
import br.com.afitcenter.nutricionista.model.entity.Nutricionista;
import br.com.afitcenter.nutricionista.repository.NutricionistaRepository;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Cadastro público do nutricionista, distribuído entre dois serviços (saga com compensação):
 *
 * <ol>
 *     <li>valida CPF e CRN localmente (falha rápida, sem criar nada);</li>
 *     <li>cria a credencial no ms-autenticacao, que devolve o id do usuário;</li>
 *     <li>grava o perfil com o MESMO id;</li>
 *     <li>se o passo 3 falhar, remove a credencial criada no passo 2.</li>
 * </ol>
 *
 * <p>A chamada remota fica fora de transação de banco para não segurar conexão durante a rede.</p>
 */
@Slf4j
@Service
public class CadastroService {

    private final NutricionistaRepository repository;
    private final AutenticacaoClient autenticacaoClient;
    private final TransactionTemplate transacao;

    public CadastroService(NutricionistaRepository repository, AutenticacaoClient autenticacaoClient,
                           PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.autenticacaoClient = autenticacaoClient;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public CadastroResponse cadastrar(CadastroRequest dados, String ip) {
        String cpf = Cpf.normalizar(dados.cpf());
        if (repository.existsByCpf(cpf))
            throw new ConflitoException("Já existe um nutricionista cadastrado com este CPF.");
        if (repository.existsByCrnRegiaoAndCrnNumero(dados.crnRegiao(), dados.crnNumero()))
            throw new ConflitoException("Já existe um nutricionista cadastrado com este CRN.");

        String email = dados.email().trim().toLowerCase(Locale.ROOT);
        UUID usuarioId = criarCredencial(dados, email, ip);

        try {
            transacao.executeWithoutResult(status -> repository.save(new Nutricionista(
                    usuarioId, dados.nome().trim(), cpf, dados.crnRegiao(), dados.crnNumero(), email, dados.telefone())));
        } catch (RuntimeException e) {
            compensar(usuarioId);
            if (e instanceof DataIntegrityViolationException)
                throw new ConflitoException("Já existe um nutricionista cadastrado com este CPF ou CRN.");
            throw e;
        }
        // A requisição é anônima: a trilha registra quem acabou de ser cadastrado.
        AuditoriaRequisicao.usuario(usuarioId);
        AuditoriaRequisicao.nutricionista(usuarioId);
        AuditoriaRequisicao.recursoId(usuarioId);
        return new CadastroResponse(usuarioId);
    }

    private UUID criarCredencial(CadastroRequest dados, String email, String ip) {
        var aceites = List.of(
                new AceiteRequest("TERMOS_DE_USO", dados.versaoTermos()),
                new AceiteRequest("POLITICA_PRIVACIDADE", dados.versaoTermos()));
        try {
            return autenticacaoClient.criar(new CriarUsuarioRequest(email, dados.senha(), "NUTRICIONISTA", aceites, ip)).id();
        } catch (FeignException.Conflict e) {
            throw new ConflitoException("Já existe uma conta com este e-mail.");
        }
    }

    private void compensar(UUID usuarioId) {
        try {
            autenticacaoClient.remover(usuarioId);
        } catch (RuntimeException e) {
            // A credencial fica sem perfil: o login funciona, mas as rotas do nutricionista respondem 404.
            log.error("Compensação do cadastro falhou: usuário {} ficou sem perfil e precisa ser removido manualmente.",
                    usuarioId, e);
        }
    }
}
