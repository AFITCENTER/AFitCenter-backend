package br.com.afitcenter.dieta.service;

import br.com.afitcenter.dieta.integracao.PacienteCondicaoClient;
import br.com.afitcenter.dieta.model.dto.Dtos.AlertaResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.AlertasResponse;
import br.com.afitcenter.dieta.model.entity.Alimento;
import br.com.afitcenter.dieta.model.entity.CondicaoRestricao;
import br.com.afitcenter.dieta.model.entity.Dieta;
import br.com.afitcenter.dieta.model.entity.Refeicao;
import br.com.afitcenter.dieta.model.entity.RefeicaoItem;
import br.com.afitcenter.dieta.model.entity.RefeicaoItemSubstituto;
import br.com.afitcenter.dieta.model.enums.Enums.MotivoRestricao;
import br.com.afitcenter.dieta.repository.CondicaoRestricaoRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cruza as condições ativas do paciente (ms-paciente) com {@code condicao_restricao}, por alimento ou
 * pelo grupo do alimento, e aponta os itens e substitutos em conflito. Modelos não têm paciente:
 * não há o que cruzar.
 */
@Service
@RequiredArgsConstructor
public class AlertaRestricaoService {

    private final DietaService dietaService;
    private final ConteudoDietaService conteudoService;
    private final CondicaoRestricaoRepository restricaoRepository;
    private final PacienteCondicaoClient pacienteCondicaoClient;

    @Transactional(readOnly = true)
    public AlertasResponse alertas(UUID nutricionistaId, UUID id) {
        Dieta dieta = dietaService.entidade(nutricionistaId, id);
        if (dieta.isModelo()) return new AlertasResponse(id, List.of(), List.of());

        List<Integer> condicoes = condicoesAtivas(dieta.getPacienteId());
        List<CondicaoRestricao> restricoes = condicoes.isEmpty() ? List.of()
                : restricaoRepository.findAllByCondicaoIdIn(condicoes);
        if (restricoes.isEmpty()) return new AlertasResponse(id, condicoes, List.of());

        ConteudoDieta conteudo = conteudoService.carregar(id);
        List<AlertaResponse> alertas = new ArrayList<>();
        for (Refeicao refeicao : conteudo.refeicoes()) {
            for (RefeicaoItem item : conteudo.itens(refeicao.getId())) {
                alertas.addAll(conflitos(refeicao, item, null, conteudo.alimento(item.getAlimentoId()), restricoes));
                for (RefeicaoItemSubstituto substituto : conteudo.substitutos(item.getId()))
                    alertas.addAll(conflitos(refeicao, item, substituto, conteudo.alimento(substituto.getAlimentoId()),
                            restricoes));
            }
        }
        return new AlertasResponse(id, condicoes, alertas);
    }

    /** Um alerta por condição; se a condição restringe o alimento e também o grupo, vale o alimento. */
    private static List<AlertaResponse> conflitos(Refeicao refeicao, RefeicaoItem item, RefeicaoItemSubstituto substituto,
                                                  Alimento alimento, List<CondicaoRestricao> restricoes) {
        Integer grupoId = alimento.getGrupo() == null ? null : alimento.getGrupo().getId();
        Map<Integer, MotivoRestricao> porCondicao = new TreeMap<>();
        for (CondicaoRestricao restricao : restricoes) {
            if (alimento.getId().equals(restricao.getAlimentoId()))
                porCondicao.put(restricao.getCondicaoId(), MotivoRestricao.ALIMENTO);
            else if (grupoId != null && grupoId.equals(restricao.getGrupoAlimentarId()))
                porCondicao.putIfAbsent(restricao.getCondicaoId(), MotivoRestricao.GRUPO);
        }
        return porCondicao.entrySet().stream()
                .map(e -> new AlertaResponse(refeicao.getId(), refeicao.getNome(), item.getId(),
                        substituto == null ? null : substituto.getId(), alimento.getId(), alimento.getNome(), grupoId,
                        alimento.getGrupo() == null ? null : alimento.getGrupo().getNome(), e.getKey(), e.getValue()))
                .toList();
    }

    /** Paciente desconhecido no ms-paciente conta como sem condições; outras falhas viram 502. */
    private List<Integer> condicoesAtivas(UUID pacienteId) {
        try {
            List<Integer> ids = pacienteCondicaoClient.condicoesAtivas(pacienteId);
            return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
        } catch (FeignException.NotFound e) {
            return List.of();
        }
    }
}
