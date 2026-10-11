package br.com.afitcenter.dieta.service;

import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.AlimentoDetalheResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.AlimentoRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.AlimentoResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.MedidaResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.NutrienteAlimentoResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.NutrienteQuantidade;
import br.com.afitcenter.dieta.model.entity.Alimento;
import br.com.afitcenter.dieta.model.entity.GrupoAlimentar;
import br.com.afitcenter.dieta.model.entity.Nutriente;
import br.com.afitcenter.dieta.repository.AlimentoMedidaRepository;
import br.com.afitcenter.dieta.repository.AlimentoRepository;
import br.com.afitcenter.dieta.repository.AlimentoRepository.Filtros;
import br.com.afitcenter.dieta.repository.GrupoAlimentarRepository;
import br.com.afitcenter.dieta.repository.NutrienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo de alimentos: a base oficial (TACO/TBCA) é compartilhada e somente leitura; os alimentos
 * próprios pertencem ao nutricionista e só ele os enxerga. Exclusão lógica ({@code ativo = false}).
 */
@Service
@RequiredArgsConstructor
public class AlimentoService {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;
    /** Usado na verificação de nome repetido ao criar (nenhum alimento tem este id). */
    private static final Integer NENHUM = 0;

    private final AlimentoRepository repository;
    private final AlimentoMedidaRepository medidaRepository;
    private final GrupoAlimentarRepository grupoRepository;
    private final NutrienteRepository nutrienteRepository;

    @Transactional(readOnly = true)
    public PaginaResponse<AlimentoResponse> buscar(UUID nutricionistaId, String busca, Integer grupoId, Boolean proprios,
                                                   int pagina, int tamanho) {
        var filtro = Specification.where(Filtros.visiveisPara(nutricionistaId))
                .and(Filtros.comNome(busca))
                .and(Filtros.doGrupo(grupoId))
                .and(Filtros.proprios(proprios));
        var paginacao = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA), Sort.by("nome"));
        return PaginaResponse.de(repository.findAll(filtro, paginacao), AlimentoResponse::de);
    }

    @Transactional(readOnly = true)
    public AlimentoDetalheResponse detalhe(UUID nutricionistaId, Integer id) {
        return detalhe(visivel(nutricionistaId, id));
    }

    @Transactional
    public AlimentoDetalheResponse criar(UUID nutricionistaId, AlimentoRequest dados) {
        Alimento alimento = new Alimento(nutricionistaId);
        preencher(alimento, nutricionistaId, dados, NENHUM);
        return detalhe(repository.save(alimento));
    }

    @Transactional
    public AlimentoDetalheResponse atualizar(UUID nutricionistaId, Integer id, AlimentoRequest dados) {
        Alimento alimento = proprio(nutricionistaId, id);
        preencher(alimento, nutricionistaId, dados, id);
        return detalhe(repository.saveAndFlush(alimento));
    }

    /** Exclusão lógica: some da busca, mas as dietas que já o usam continuam válidas. */
    @Transactional
    public void excluir(UUID nutricionistaId, Integer id) {
        proprio(nutricionistaId, id).desativar();
    }

    /** Ativo e da base oficial ou do próprio nutricionista; caso contrário, 404. */
    @Transactional(readOnly = true)
    public Alimento visivel(UUID nutricionistaId, Integer id) {
        return repository.buscarVisivel(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alimento não encontrado."));
    }

    /** Alimento próprio do nutricionista: o único que pode ser alterado. */
    @Transactional(readOnly = true)
    public Alimento proprio(UUID nutricionistaId, Integer id) {
        Alimento alimento = visivel(nutricionistaId, id);
        if (!alimento.proprio())
            throw new RegraNegocioException("A base oficial (TACO/TBCA) é somente leitura. Cadastre um alimento próprio.");
        return alimento;
    }

    private void preencher(Alimento alimento, UUID nutricionistaId, AlimentoRequest dados, Integer ignorarId) {
        String nome = dados.nome().trim();
        if (repository.existsByNutricionistaIdAndAtivoTrueAndNomeIgnoreCaseAndIdNot(nutricionistaId, nome, ignorarId))
            throw new ConflitoException("Você já tem um alimento próprio com esse nome.");

        GrupoAlimentar grupo = dados.grupoId() == null ? null : grupoRepository.findById(dados.grupoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Grupo alimentar não encontrado."));
        alimento.preencher(grupo, nome, dados.porcaoOuPadrao(), dados.kcal(), dados.proteinaG(), dados.carboidratoG(),
                dados.lipidioG(), dados.fibraG(), nutrientes(dados.nutrientesOuVazio()));
    }

    private Map<Integer, BigDecimal> nutrientes(List<NutrienteQuantidade> lista) {
        Map<Integer, BigDecimal> nutrientes = new HashMap<>();
        for (NutrienteQuantidade n : lista)
            if (nutrientes.put(n.nutrienteId(), n.quantidade()) != null)
                throw new RegraNegocioException("O nutriente " + n.nutrienteId() + " foi informado mais de uma vez.");
        if (nutrienteRepository.findAllById(nutrientes.keySet()).size() != nutrientes.size())
            throw new RecursoNaoEncontradoException("Nutriente não encontrado.");
        return nutrientes;
    }

    private AlimentoDetalheResponse detalhe(Alimento alimento) {
        List<MedidaResponse> medidas = medidaRepository.findAllByAlimentoIdOrderByGramasAscDescricaoAsc(alimento.getId())
                .stream().map(MedidaResponse::de).toList();
        Map<Integer, Nutriente> catalogo = nutrienteRepository.findAllById(alimento.getNutrientes().keySet()).stream()
                .collect(Collectors.toMap(Nutriente::getId, Function.identity()));
        List<NutrienteAlimentoResponse> nutrientes = alimento.getNutrientes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(e -> {
                    Nutriente n = catalogo.get(e.getKey());
                    return new NutrienteAlimentoResponse(n.getId(), n.getNome(), n.getUnidade(), e.getValue());
                })
                .toList();
        return AlimentoDetalheResponse.de(alimento, medidas, nutrientes);
    }
}
