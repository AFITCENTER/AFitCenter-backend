package br.com.afitcenter.consulta.model.dto;

import br.com.afitcenter.consulta.model.entity.Analise;
import br.com.afitcenter.consulta.model.entity.Consulta;
import br.com.afitcenter.consulta.model.enums.Enums.CategoriaAnalise;
import br.com.afitcenter.consulta.model.enums.Enums.Modalidade;
import br.com.afitcenter.consulta.model.enums.Enums.StatusConsulta;
import br.com.afitcenter.consulta.model.enums.Enums.TipoConsulta;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Contratos HTTP do ms-consulta. */
public final class Dtos {

    private Dtos() {
    }

    public record ConsultaRequest(
            @NotNull UUID pacienteId,
            UUID consultorioId,
            @NotNull OffsetDateTime dataHora,
            @Min(5) @Max(480) Integer duracaoMin,
            @NotNull TipoConsulta tipo,
            @NotNull Modalidade modalidade,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal valor,
            @Size(max = 4000) String observacao
    ) {

        public int duracaoOuPadrao() {
            return duracaoMin == null ? 60 : duracaoMin;
        }
    }

    public record StatusRequest(@NotNull StatusConsulta status) {
    }

    public record ConsultaResponse(
            UUID id,
            UUID pacienteId,
            String pacienteNome,
            UUID consultorioId,
            String consultorioNome,
            OffsetDateTime dataHora,
            Integer duracaoMin,
            TipoConsulta tipo,
            Modalidade modalidade,
            StatusConsulta status,
            BigDecimal valor,
            String observacao,
            Instant criadoEm,
            Instant atualizadoEm
    ) {

        public static ConsultaResponse de(Consulta c, String pacienteNome, String consultorioNome) {
            return new ConsultaResponse(c.getId(), c.getPacienteId(), pacienteNome, c.getConsultorioId(),
                    consultorioNome, c.getDataHora(), c.getDuracaoMin(), c.getTipo(), c.getModalidade(), c.getStatus(),
                    c.getValor(), c.getObservacao(), c.getCriadoEm(), c.getAtualizadoEm());
        }
    }

    public record AnaliseRequest(
            @NotNull CategoriaAnalise categoria,
            @NotBlank @Size(max = 10000) String descricao
    ) {
    }

    public record AnaliseResponse(UUID id, UUID consultaId, CategoriaAnalise categoria, String descricao,
                                  Instant criadoEm, Instant atualizadoEm) {

        public static AnaliseResponse de(Analise a) {
            return new AnaliseResponse(a.getId(), a.getConsultaId(), a.getCategoria(), a.getDescricao(),
                    a.getCriadoEm(), a.getAtualizadoEm());
        }
    }

    /** {@code GET /internal/consultas/{id}}: ms-avaliacao e ms-dieta validam o vínculo com a consulta. */
    public record ConsultaInternoResponse(UUID id, UUID pacienteId, UUID nutricionistaId, OffsetDateTime dataHora,
                                          StatusConsulta status) {

        public static ConsultaInternoResponse de(Consulta c) {
            return new ConsultaInternoResponse(c.getId(), c.getPacienteId(), c.getNutricionistaId(), c.getDataHora(),
                    c.getStatus());
        }
    }
}
