package br.com.afitcenter.planos.service;

import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.planos.model.dto.Dtos.PlanoRequest;
import br.com.afitcenter.planos.model.dto.Dtos.PlanoResponse;
import br.com.afitcenter.planos.model.entity.Plano;
import br.com.afitcenter.planos.repository.PlanoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catálogo de planos: leitura pública e manutenção pelo administrador. Mudar os limites de um plano
 * republica as assinaturas afetadas, para que o ms-paciente e o ms-nutricionista apliquem os novos valores.
 *
 * <p>O preço vale para as próximas faturas (inclusive renovações de quem já assina); faturas emitidas não mudam.</p>
 */
@Service
@RequiredArgsConstructor
public class PlanoService {

    private final PlanoRepository repository;
    private final AssinaturaService assinaturaService;

    @Transactional(readOnly = true)
    public List<PlanoResponse> catalogo() {
        return repository.findAllByAtivoTrueAndPublicoTrueOrderByOrdemAscIdAsc().stream().map(PlanoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public PlanoResponse buscarPublico(Integer id) {
        return repository.findByIdAndAtivoTrue(id).filter(Plano::isPublico).map(PlanoResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Plano não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<PlanoResponse> listarTodos() {
        return repository.findAllByOrderByOrdemAscIdAsc().stream().map(PlanoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public PlanoResponse buscar(Integer id) {
        return PlanoResponse.de(entidade(id));
    }

    @Transactional
    public PlanoResponse criar(PlanoRequest dados) {
        validarPrecos(dados, false);
        if (repository.existsByCodigo(dados.codigo()))
            throw new ConflitoException("Já existe um plano com este código.");
        Plano plano = repository.saveAndFlush(new Plano(dados.codigo(), dados.nome().trim(), dados.descricao(),
                dados.precoMensal(), dados.precoAnual(), dados.limitePacientes(), dados.limiteConsultorios(),
                dados.publicoOuPadrao(), dados.ordemOuPadrao()));
        return PlanoResponse.de(plano);
    }

    @Transactional
    public PlanoResponse atualizar(Integer id, PlanoRequest dados) {
        Plano plano = entidade(id);
        if (!plano.getCodigo().equals(dados.codigo()))
            throw new RegraNegocioException("O código do plano não pode ser alterado.");
        validarPrecos(dados, plano.isPadrao());

        boolean limitesMudaram = !plano.mesmosLimites(dados.limitePacientes(), dados.limiteConsultorios());
        plano.atualizar(dados.nome().trim(), dados.descricao(), dados.precoMensal(), dados.precoAnual(),
                dados.limitePacientes(), dados.limiteConsultorios(), dados.publicoOuPadrao(), dados.ordemOuPadrao());
        repository.saveAndFlush(plano);
        PlanoResponse resposta = PlanoResponse.de(plano);
        if (limitesMudaram) assinaturaService.republicarLimites(plano);
        return resposta;
    }

    /** Desativar só impede novas assinaturas; quem já assina continua renovando. */
    @Transactional
    public PlanoResponse alterarAtivo(Integer id, boolean ativo) {
        Plano plano = entidade(id);
        if (plano.isPadrao() && !ativo)
            throw new RegraNegocioException("O plano padrão não pode ser desativado.");
        plano.alterarAtivo(ativo);
        return PlanoResponse.de(repository.saveAndFlush(plano));
    }

    private static void validarPrecos(PlanoRequest dados, boolean padrao) {
        boolean gratuito = dados.precoMensal().signum() == 0;
        if (gratuito && dados.precoAnual() != null)
            throw new RegraNegocioException("Plano gratuito não tem preço anual.");
        if (padrao && !gratuito)
            throw new RegraNegocioException("O plano padrão precisa continuar gratuito.");
    }

    private Plano entidade(Integer id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Plano não encontrado."));
    }
}
