package br.com.afitcenter.auditoria.controller;

import br.com.afitcenter.auditoria.model.dto.Dtos.FiltroRegistros;
import br.com.afitcenter.auditoria.model.dto.Dtos.RegistroResponse;
import br.com.afitcenter.auditoria.service.RegistroService;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auditoria/registros")
@RequiredArgsConstructor
@Tag(name = "Auditoria", description = "Quem fez o quê nos dados do nutricionista logado (LGPD): acessos e alterações "
        + "em prontuários, ações do sistema e do administrador.")
public class RegistroController {

    private final RegistroService service;

    @GetMapping
    @Operation(summary = "Trilha de auditoria", description = "Mais recentes primeiro. Para saber quem acessou um "
            + "paciente, filtre por pacienteId.")
    public PaginaResponse<RegistroResponse> listar(
            @Parameter(description = "Início do período (ISO 8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @Parameter(description = "Fim do período, exclusivo")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim,
            @RequestParam(required = false) UUID pacienteId,
            @RequestParam(required = false) UUID usuarioId,
            @Parameter(description = "Ex.: ms-paciente") @RequestParam(required = false) String servico,
            @Parameter(description = "Ex.: pacientes, consultas, dietas") @RequestParam(required = false) String recurso,
            @RequestParam(required = false) String recursoId,
            @Parameter(description = "LEITURA, CRIACAO, ALTERACAO, EXCLUSAO, LOGIN...") @RequestParam(required = false) String acao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        var filtro = new FiltroRegistros(inicio == null ? null : inicio.toInstant(), fim == null ? null : fim.toInstant(),
                null, pacienteId, usuarioId, servico, recurso, recursoId, acao, null);
        return service.doNutricionista(UsuarioAutenticado.nutricionistaId(), filtro, pagina, tamanho);
    }
}
