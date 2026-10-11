package br.com.afitcenter.avaliacao.controller;

import br.com.afitcenter.avaliacao.model.entity.Metrica;
import br.com.afitcenter.avaliacao.model.entity.RegiaoCorporal;
import br.com.afitcenter.avaliacao.repository.MetricaRepository;
import br.com.afitcenter.avaliacao.repository.RegiaoCorporalRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** Catálogos usados para montar os formulários de avaliação e de exame. */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Catálogos", description = "Métricas e regiões corporais.")
public class CatalogoController {

    private final MetricaRepository metricaRepository;
    private final RegiaoCorporalRepository regiaoRepository;

    public record MetricaResponse(Integer id, String codigo, String nome, String unidade, String categoria,
                                  Integer casasDecimais) {
        static MetricaResponse de(Metrica m) {
            return new MetricaResponse(m.getId(), m.getCodigo(), m.getNome(), m.getUnidade(), m.getCategoria(),
                    m.getCasasDecimais());
        }
    }

    public record RegiaoResponse(Integer id, String codigo, String nome, String tipo) {
        static RegiaoResponse de(RegiaoCorporal r) {
            return new RegiaoResponse(r.getId(), r.getCodigo(), r.getNome(), r.getTipo());
        }
    }

    @GetMapping("/metricas")
    @Transactional(readOnly = true)
    @Operation(summary = "Métricas", description = "Categorias: BASICA, COMPOSICAO, INDICE, SEGMENTAR, IMPEDANCIA, "
            + "PERIMETRO, DOBRA_CUTANEA, LABORATORIAL.")
    public List<MetricaResponse> metricas(@RequestParam(required = false) String categoria) {
        var metricas = categoria == null ? metricaRepository.findAllByOrderByCategoriaAscIdAsc()
                : metricaRepository.findAllByCategoriaOrderByIdAsc(categoria.toUpperCase(Locale.ROOT));
        return metricas.stream().map(MetricaResponse::de).toList();
    }

    @GetMapping("/regioes-corporais")
    @Transactional(readOnly = true)
    @Operation(summary = "Regiões corporais", description = "Tipos: SEGMENTO, PERIMETRO, DOBRA.")
    public List<RegiaoResponse> regioes(@RequestParam(required = false) String tipo) {
        var regioes = tipo == null ? regiaoRepository.findAllByOrderByTipoAscIdAsc()
                : regiaoRepository.findAllByTipoOrderByIdAsc(tipo.toUpperCase(Locale.ROOT));
        return regioes.stream().map(RegiaoResponse::de).toList();
    }
}
