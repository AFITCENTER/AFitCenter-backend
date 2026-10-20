package br.com.afitcenter.nutricionista.service;

import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.base.validacao.Cpf;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.nutricionista.client.PessoaClient;
import br.com.afitcenter.nutricionista.model.dto.Dtos.AceiteRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.CadastroRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.CadastroResponse;
import br.com.afitcenter.nutricionista.model.dto.Dtos.RegistrarPessoaRequest;
import br.com.afitcenter.nutricionista.model.entity.Nutricionista;
import br.com.afitcenter.nutricionista.repository.NutricionistaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Cadastro público do nutricionista, distribuído entre dois serviços (saga com compensação):
 *
 * <ol>
 *     <li>valida o CRN localmente (falha rápida, sem criar nada);</li>
 *     <li>registra a pessoa no ms-pessoa (nome, CPF, e-mail, senha, telefone e termos), que devolve o id. Se o
 *     e-mail já tem conta e a senha confere, a conta só ganha o papel de nutricionista;</li>
 *     <li>grava o perfil profissional com o MESMO id;</li>
 *     <li>se o passo 3 falhar, tira o papel no ms-pessoa, que remove a conta se ela ficar sem nenhum.</li>
 * </ol>
 *
 * <p>A chamada remota fica fora de transação de banco para não segurar conexão durante a rede.</p>
 */
@Slf4j
@Service
public class CadastroService {

    static final String PAPEL = "NUTRICIONISTA";

    private final NutricionistaRepository repository;
    private final PessoaClient pessoaClient;
    private final TransactionTemplate transacao;
    private final ObjectMapper json;

    public CadastroService(NutricionistaRepository repository, PessoaClient pessoaClient,
                           PlatformTransactionManager transactionManager, ObjectMapper json) {
        this.repository = repository;
        this.pessoaClient = pessoaClient;
        this.transacao = new TransactionTemplate(transactionManager);
        this.json = json;
    }

    public CadastroResponse cadastrar(CadastroRequest dados, String ip) {
        if (repository.existsByCrnRegiaoAndCrnNumero(dados.crnRegiao(), dados.crnNumero()))
            throw new ConflitoException("Já existe um nutricionista cadastrado com este CRN.");

        UUID pessoaId = registrarPessoa(dados, ip);

        try {
            transacao.executeWithoutResult(status -> repository.save(
                    new Nutricionista(pessoaId, dados.crnRegiao(), dados.crnNumero())));
        } catch (RuntimeException e) {
            compensar(pessoaId);
            if (e instanceof DataIntegrityViolationException)
                throw new ConflitoException("Já existe um nutricionista cadastrado com este CRN ou com esta conta.");
            throw e;
        }
        // A requisição é anônima: a trilha registra quem acabou de ser cadastrado.
        AuditoriaRequisicao.usuario(pessoaId);
        AuditoriaRequisicao.nutricionista(pessoaId);
        AuditoriaRequisicao.recursoId(pessoaId);
        return new CadastroResponse(pessoaId);
    }

    /** As recusas do ms-pessoa já vêm escritas para quem está se cadastrando: o motivo é repassado. */
    private UUID registrarPessoa(CadastroRequest dados, String ip) {
        var aceites = List.of(
                new AceiteRequest("TERMOS_DE_USO", dados.versaoTermos()),
                new AceiteRequest("POLITICA_PRIVACIDADE", dados.versaoTermos()));
        try {
            return pessoaClient.registrar(new RegistrarPessoaRequest(dados.nome().trim(), Cpf.normalizar(dados.cpf()),
                    dados.email(), dados.senha(), dados.telefone(), PAPEL, aceites, ip)).id();
        } catch (FeignException.Conflict e) {
            throw new ConflitoException(motivo(e, "Já existe uma conta com este e-mail ou CPF."));
        } catch (FeignException.UnprocessableEntity e) {
            throw new RegraNegocioException(motivo(e, "Não foi possível usar esta conta para o cadastro."));
        } catch (FeignException.TooManyRequests e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    motivo(e, "Muitas tentativas com senha incorreta. Tente novamente em alguns minutos."));
        }
    }

    /** Campo {@code detail} do Problem Details devolvido pelo ms-pessoa. */
    private String motivo(FeignException e, String padrao) {
        try {
            String detalhe = json.readTree(e.contentUTF8()).path("detail").asText("");
            return detalhe.isBlank() ? padrao : detalhe;
        } catch (Exception ilegivel) {
            return padrao;
        }
    }

    private void compensar(UUID pessoaId) {
        try {
            pessoaClient.removerPapel(pessoaId, PAPEL);
        } catch (RuntimeException e) {
            // A conta fica com o papel e sem perfil: o login funciona, mas as rotas do nutricionista respondem 404.
            log.error("Compensação do cadastro falhou: a pessoa {} ficou com o papel {} sem perfil e precisa de ajuste "
                    + "manual no ms-pessoa.", pessoaId, PAPEL, e);
        }
    }
}
