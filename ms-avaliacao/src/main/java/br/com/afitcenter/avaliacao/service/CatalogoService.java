package br.com.afitcenter.avaliacao.service;

import br.com.afitcenter.avaliacao.model.entity.Metrica;
import br.com.afitcenter.avaliacao.model.entity.RegiaoCorporal;
import br.com.afitcenter.avaliacao.repository.MetricaRepository;
import br.com.afitcenter.avaliacao.repository.RegiaoCorporalRepository;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolve códigos de métrica e região vindos da API. Código desconhecido é erro de quem chama (422),
 * não recurso inexistente.
 */
@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final MetricaRepository metricaRepository;
    private final RegiaoCorporalRepository regiaoRepository;

    /** Métricas por código (códigos já normalizados); 422 se algum não existir. */
    public Map<String, Metrica> metricas(Collection<String> codigos) {
        if (codigos.isEmpty()) return Map.of();
        Map<String, Metrica> encontradas = metricaRepository.findAllByCodigoIn(Set.copyOf(codigos)).stream()
                .collect(Collectors.toMap(Metrica::getCodigo, Function.identity()));
        exigirTodos(codigos, encontradas, "Métrica desconhecida: ");
        return encontradas;
    }

    public Metrica metrica(String codigo) {
        return metricas(Set.of(codigo)).get(codigo);
    }

    /** Regiões por código (códigos já normalizados); 422 se algum não existir. */
    public Map<String, RegiaoCorporal> regioes(Collection<String> codigos) {
        if (codigos.isEmpty()) return Map.of();
        Map<String, RegiaoCorporal> encontradas = regiaoRepository.findAllByCodigoIn(Set.copyOf(codigos)).stream()
                .collect(Collectors.toMap(RegiaoCorporal::getCodigo, Function.identity()));
        exigirTodos(codigos, encontradas, "Região corporal desconhecida: ");
        return encontradas;
    }

    /** Código em maiúsculas, sem espaços; vazio vira nulo. */
    public static String normalizar(String codigo) {
        return codigo == null || codigo.isBlank() ? null : codigo.trim().toUpperCase(Locale.ROOT);
    }

    /** Faixa de referência impressa no laudo: o mínimo não pode passar do máximo. */
    public static void validarFaixa(String metrica, BigDecimal refMin, BigDecimal refMax) {
        if (refMin != null && refMax != null && refMin.compareTo(refMax) > 0)
            throw new RegraNegocioException("Faixa de referência inválida em " + metrica
                    + ": o mínimo é maior que o máximo.");
    }

    private static void exigirTodos(Collection<String> codigos, Map<String, ?> encontrados, String mensagem) {
        Set<String> faltando = new TreeSet<>(codigos);
        faltando.removeAll(encontrados.keySet());
        if (!faltando.isEmpty())
            throw new RegraNegocioException(mensagem + String.join(", ", faltando) + ".");
    }
}
