package br.com.afitcenter.paciente.controller.internal;

import br.com.afitcenter.paciente.model.dto.Dtos.PacienteInternoResponse;
import br.com.afitcenter.paciente.service.PacienteCondicaoService;
import br.com.afitcenter.paciente.service.PacienteService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Rotas para os outros serviços (token interno). O Gateway as bloqueia. */
@Hidden
@RestController
@RequestMapping("/internal/pacientes")
@RequiredArgsConstructor
public class PacienteInternoController {

    private final PacienteService pacienteService;
    private final PacienteCondicaoService condicaoService;

    /** Usado pela réplica (core-paciente) quando o evento de criação ainda não chegou. */
    @GetMapping("/{id}")
    public PacienteInternoResponse buscar(@PathVariable UUID id) {
        return pacienteService.buscarInterno(id);
    }

    /** Ids das condições ativas, para o ms-dieta alertar alimentos restritos. */
    @GetMapping("/{id}/condicoes-ativas")
    public List<Integer> condicoesAtivas(@PathVariable UUID id) {
        return condicaoService.condicoesAtivas(id);
    }
}
