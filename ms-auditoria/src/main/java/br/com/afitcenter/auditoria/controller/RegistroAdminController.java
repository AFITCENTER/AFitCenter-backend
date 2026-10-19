package br.com.afitcenter.auditoria.controller;

import br.com.afitcenter.auditoria.model.dto.Dtos.FiltroRegistros;
import br.com.afitcenter.auditoria.model.dto.Dtos.RegistroResponse;
import br.com.afitcenter.auditoria.service.RegistroService;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/auditoria/registros")
@RequiredArgsConstructor
@Tag(name = "Auditoria (administração)", description = "Trilha de todos os tenants. Exige o papel ADMIN; cada consulta "
        + "também é registrada.")
public class RegistroAdminController {

    private final RegistroService service;

    @GetMapping
    @Operation(summary = "Trilha de auditoria de todos os nutricionistas")
    public PaginaResponse<RegistroResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim,
            @RequestParam(required = false) UUID nutricionistaId,
            @RequestParam(required = false) UUID pacienteId,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) String servico,
            @RequestParam(required = false) String recurso,
            @RequestParam(required = false) String recursoId,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho,
            HttpServletRequest request) {
        var filtro = new FiltroRegistros(inicio == null ? null : inicio.toInstant(), fim == null ? null : fim.toInstant(),
                nutricionistaId, pacienteId, usuarioId, servico, recurso, recursoId, acao, ip);
        return service.doAdministrador(UsuarioAutenticado.usuarioId(), request.getRemoteAddr(), filtro, pagina, tamanho);
    }
}
