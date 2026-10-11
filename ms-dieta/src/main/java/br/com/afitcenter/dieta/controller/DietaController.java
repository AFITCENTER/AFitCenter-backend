package br.com.afitcenter.dieta.controller;

import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.dieta.model.dto.Dtos.AlertasResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.DietaDetalheResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.DietaRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.DietaResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.StatusRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.TotaisResponse;
import br.com.afitcenter.dieta.model.enums.Enums.StatusDieta;
import br.com.afitcenter.dieta.service.AlertaRestricaoService;
import br.com.afitcenter.dieta.service.DietaService;
import br.com.afitcenter.dieta.service.TotaisDietaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/dietas")
@RequiredArgsConstructor
@Tag(name = "Dietas", description = "Planos alimentares dos pacientes e modelos reutilizáveis (requisito 5).")
public class DietaController {

    private final DietaService service;
    private final TotaisDietaService totaisService;
    private final AlertaRestricaoService alertaService;

    @GetMapping
    @Operation(summary = "Listar dietas de pacientes",
            description = "Mais recentes primeiro. Modelos ficam em GET /v1/modelos.")
    public PaginaResponse<DietaResponse> listar(@RequestParam(required = false) UUID pacienteId,
                                                @RequestParam(required = false) StatusDieta status,
                                                @RequestParam(defaultValue = "0") int pagina,
                                                @RequestParam(defaultValue = "50") int tamanho) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), pacienteId, status, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar dieta ou modelo", description = "Com refeições, itens, substitutos e macros de cada item.")
    public DietaDetalheResponse buscar(@PathVariable UUID id) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), id);
    }

    @PostMapping
    @Operation(summary = "Criar dieta ou modelo",
            description = "Dieta: informe `pacienteId` (e opcionalmente `consultaId`, validada no ms-consulta). "
                    + "Modelo: `modelo = true`, sem paciente nem consulta. Nasce em RASCUNHO.")
    public ResponseEntity<DietaResponse> criar(@Valid @RequestBody DietaRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(UsuarioAutenticado.nutricionistaId(), dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar dados gerais",
            description = "Paciente e `modelo` não mudam. Dieta ENCERRADA responde 422: crie uma nova versão.")
    public DietaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody DietaRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), id, dados);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Mudar status",
            description = "RASCUNHO → ATIVA/ENCERRADA; ATIVA → ENCERRADA. Só uma dieta ATIVA por paciente (409).")
    public DietaResponse alterarStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest dados) {
        return service.alterarStatus(UsuarioAutenticado.nutricionistaId(), id, dados.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir dieta ou modelo (exclusão lógica)")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/nova-versao")
    @Operation(summary = "Nova versão",
            description = "Cópia profunda ligada à anterior (`dietaOrigemId`). Se a anterior estava ATIVA, ela vira "
                    + "ENCERRADA e a nova já nasce ATIVA; senão, a nova nasce RASCUNHO. Datas e consulta não são copiadas.")
    public ResponseEntity<DietaDetalheResponse> novaVersao(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.novaVersao(UsuarioAutenticado.nutricionistaId(), id));
    }

    @GetMapping("/{id}/totais")
    @Operation(summary = "Totais de energia e macros",
            description = "Por refeição (opção 1; as demais opções à parte) e por dia da semana, comparados com as metas.")
    public TotaisResponse totais(@PathVariable UUID id) {
        return totaisService.totais(UsuarioAutenticado.nutricionistaId(), id);
    }

    @GetMapping("/{id}/alertas")
    @Operation(summary = "Alertas de restrição",
            description = "Itens e substitutos com alimentos (ou grupos) restritos pelas condições ativas do paciente. "
                    + "Modelos não têm paciente e voltam sem alertas.")
    public AlertasResponse alertas(@PathVariable UUID id) {
        return alertaService.alertas(UsuarioAutenticado.nutricionistaId(), id);
    }
}
