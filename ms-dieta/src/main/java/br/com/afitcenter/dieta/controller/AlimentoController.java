package br.com.afitcenter.dieta.controller;

import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.dieta.model.dto.Dtos.AlimentoDetalheResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.AlimentoRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.AlimentoResponse;
import br.com.afitcenter.dieta.service.AlimentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/alimentos")
@RequiredArgsConstructor
@Tag(name = "Alimentos", description = "Base oficial (TACO/TBCA, somente leitura) e alimentos próprios do nutricionista.")
public class AlimentoController {

    private final AlimentoService service;

    @GetMapping
    @Operation(summary = "Buscar alimentos", description = "Base oficial + alimentos próprios, por parte do nome e grupo.")
    public PaginaResponse<AlimentoResponse> buscar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Integer grupoId,
            @Parameter(description = "true: só os próprios; false: só a base oficial; omitido: todos")
            @RequestParam(required = false) Boolean proprios,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "30") int tamanho) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), busca, grupoId, proprios, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar alimento", description = "Com medidas caseiras e micronutrientes.")
    public AlimentoDetalheResponse detalhe(@PathVariable Integer id) {
        return service.detalhe(UsuarioAutenticado.nutricionistaId(), id);
    }

    @PostMapping
    @Operation(summary = "Cadastrar alimento próprio",
            description = "Macros e micronutrientes na porção base (100 g se omitida). 409 se já houver um próprio com o mesmo nome.")
    public ResponseEntity<AlimentoDetalheResponse> criar(@Valid @RequestBody AlimentoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(UsuarioAutenticado.nutricionistaId(), dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar alimento próprio",
            description = "Substitui também a lista de micronutrientes. Alimentos da base oficial respondem 422.")
    public AlimentoDetalheResponse atualizar(@PathVariable Integer id, @Valid @RequestBody AlimentoRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir alimento próprio",
            description = "Exclusão lógica: some da busca, mas continua nas dietas que já o usam.")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }
}
