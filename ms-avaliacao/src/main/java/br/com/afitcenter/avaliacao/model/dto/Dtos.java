package br.com.afitcenter.avaliacao.model.dto;

import br.com.afitcenter.avaliacao.model.entity.Avaliacao;
import br.com.afitcenter.avaliacao.model.entity.AvaliacaoResultado;
import br.com.afitcenter.avaliacao.model.entity.Equipamento;
import br.com.afitcenter.avaliacao.model.entity.Exame;
import br.com.afitcenter.avaliacao.model.entity.ExameAnexo;
import br.com.afitcenter.avaliacao.model.entity.ExameResultado;
import br.com.afitcenter.avaliacao.model.entity.Meta;
import br.com.afitcenter.avaliacao.model.enums.Enums.MetodoAvaliacao;
import br.com.afitcenter.avaliacao.model.enums.Enums.StatusMeta;
import br.com.afitcenter.avaliacao.model.enums.Enums.TipoEquipamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP do ms-avaliacao. Métricas e regiões são referenciadas pelo código do catálogo. */
public final class Dtos {

    private Dtos() {
    }

    // ------------------------------------------------------------------ avaliação

    public record ResultadoAvaliacaoDto(
            @NotBlank @Size(max = 60) String metricaCodigo,
            @Size(max = 60) String regiaoCodigo,
            @NotNull @Digits(integer = 7, fraction = 3) BigDecimal valor,
            @Digits(integer = 7, fraction = 3) BigDecimal refMin,
            @Digits(integer = 7, fraction = 3) BigDecimal refMax,
            @Size(max = 100) String classificacao
    ) {
    }

    public record AvaliacaoRequest(
            @NotNull UUID pacienteId,
            UUID consultaId,
            Integer equipamentoId,
            @NotNull MetodoAvaliacao metodo,
            @NotNull OffsetDateTime dataHoraMedicao,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 3, fraction = 2) BigDecimal pesoKg,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 3, fraction = 1) BigDecimal alturaCm,
            Boolean emJejum,
            @Size(max = 4000) String observacao,
            @Size(max = 2000) String laudoUrl,
            @Size(max = 500) List<@NotNull @Valid ResultadoAvaliacaoDto> resultados
    ) {

        public List<ResultadoAvaliacaoDto> resultadosOuVazio() {
            return resultados == null ? List.of() : resultados;
        }
    }

    public record ResultadoAvaliacaoResponse(String metricaCodigo, String metricaNome, String unidade,
                                             String categoria, String regiaoCodigo, String regiaoNome,
                                             BigDecimal valor, BigDecimal refMin, BigDecimal refMax,
                                             String classificacao) {

        /** Ordem do catálogo: métrica, depois região (corpo inteiro primeiro). */
        static final Comparator<AvaliacaoResultado> ORDEM = Comparator
                .comparing((AvaliacaoResultado r) -> r.getMetrica().getId())
                .thenComparing(r -> r.getRegiao() == null ? 0 : r.getRegiao().getId());

        public static ResultadoAvaliacaoResponse de(AvaliacaoResultado r) {
            var m = r.getMetrica();
            var reg = r.getRegiao();
            return new ResultadoAvaliacaoResponse(m.getCodigo(), m.getNome(), m.getUnidade(), m.getCategoria(),
                    reg == null ? null : reg.getCodigo(), reg == null ? null : reg.getNome(),
                    r.getValor(), r.getRefMin(), r.getRefMax(), r.getClassificacao());
        }
    }

    public record AvaliacaoResponse(
            UUID id,
            UUID pacienteId,
            String pacienteNome,
            UUID consultaId,
            Integer equipamentoId,
            MetodoAvaliacao metodo,
            OffsetDateTime dataHoraMedicao,
            BigDecimal pesoKg,
            BigDecimal alturaCm,
            BigDecimal imc,
            Boolean emJejum,
            String observacao,
            String laudoUrl,
            List<ResultadoAvaliacaoResponse> resultados,
            Instant criadoEm,
            Instant atualizadoEm
    ) {

        public static AvaliacaoResponse de(Avaliacao a, String pacienteNome) {
            return new AvaliacaoResponse(a.getId(), a.getPacienteId(), pacienteNome, a.getConsultaId(),
                    a.getEquipamentoId(), a.getMetodo(), a.getDataHoraMedicao(), a.getPesoKg(), a.getAlturaCm(),
                    a.getImc(), a.getEmJejum(), a.getObservacao(), a.getLaudoUrl(),
                    a.getResultados().stream().sorted(ResultadoAvaliacaoResponse.ORDEM)
                            .map(ResultadoAvaliacaoResponse::de).toList(),
                    a.getCriadoEm(), a.getAtualizadoEm());
        }
    }

    /** Linha da listagem e cabeçalho da comparação (sem os resultados). */
    public record AvaliacaoResumoResponse(UUID id, UUID pacienteId, String pacienteNome, UUID consultaId,
                                          Integer equipamentoId, MetodoAvaliacao metodo,
                                          OffsetDateTime dataHoraMedicao, BigDecimal pesoKg, BigDecimal alturaCm,
                                          BigDecimal imc) {

        public static AvaliacaoResumoResponse de(Avaliacao a, String pacienteNome) {
            return new AvaliacaoResumoResponse(a.getId(), a.getPacienteId(), pacienteNome, a.getConsultaId(),
                    a.getEquipamentoId(), a.getMetodo(), a.getDataHoraMedicao(), a.getPesoKg(), a.getAlturaCm(),
                    a.getImc());
        }
    }

    // ------------------------------------------------------------------ equipamento

    public record EquipamentoRequest(
            @NotNull TipoEquipamento tipo,
            @NotBlank @Size(max = 100) String marca,
            @NotBlank @Size(max = 150) String modelo
    ) {
    }

    public record EquipamentoResponse(Integer id, TipoEquipamento tipo, String marca, String modelo,
                                      boolean doSistema) {

        public static EquipamentoResponse de(Equipamento e) {
            return new EquipamentoResponse(e.getId(), e.getTipo(), e.getMarca(), e.getModelo(), e.isDoSistema());
        }
    }

    // ------------------------------------------------------------------ meta

    public record MetaRequest(
            @NotBlank @Size(max = 60) String metricaCodigo,
            @NotNull @Digits(integer = 7, fraction = 3) BigDecimal valorAlvo,
            LocalDate dataInicio,
            LocalDate prazo,
            StatusMeta status,
            @Size(max = 2000) String observacao
    ) {
    }

    public record MetaResponse(UUID id, UUID pacienteId, String metricaCodigo, String metricaNome, String unidade,
                               BigDecimal valorAlvo, LocalDate dataInicio, LocalDate prazo, StatusMeta status,
                               String observacao, Instant criadoEm, Instant atualizadoEm) {

        public static MetaResponse de(Meta m) {
            return new MetaResponse(m.getId(), m.getPacienteId(), m.getMetrica().getCodigo(),
                    m.getMetrica().getNome(), m.getMetrica().getUnidade(), m.getValorAlvo(), m.getDataInicio(),
                    m.getPrazo(), m.getStatus(), m.getObservacao(), m.getCriadoEm(), m.getAtualizadoEm());
        }
    }

    // ------------------------------------------------------------------ exame

    public record ResultadoExameDto(
            @NotBlank @Size(max = 60) String metricaCodigo,
            @NotNull @Digits(integer = 9, fraction = 3) BigDecimal valor,
            @Digits(integer = 9, fraction = 3) BigDecimal refMin,
            @Digits(integer = 9, fraction = 3) BigDecimal refMax,
            @Size(max = 1000) String observacao
    ) {
    }

    public record ExameRequest(
            @NotNull UUID pacienteId,
            UUID consultaId,
            @NotBlank @Size(max = 200) String nome,
            @Size(max = 200) String laboratorio,
            @NotNull @PastOrPresent LocalDate dataColeta,
            @Size(max = 4000) String observacao,
            @Size(max = 200) List<@NotNull @Valid ResultadoExameDto> resultados
    ) {

        public List<ResultadoExameDto> resultadosOuVazio() {
            return resultados == null ? List.of() : resultados;
        }
    }

    public record ResultadoExameResponse(String metricaCodigo, String metricaNome, String unidade, BigDecimal valor,
                                         BigDecimal refMin, BigDecimal refMax, String observacao) {

        public static ResultadoExameResponse de(ExameResultado r) {
            var m = r.getMetrica();
            return new ResultadoExameResponse(m.getCodigo(), m.getNome(), m.getUnidade(), r.getValor(),
                    r.getRefMin(), r.getRefMax(), r.getObservacao());
        }
    }

    /** Só os metadados: o upload para o storage ainda não existe. */
    public record AnexoRequest(
            @NotBlank @Size(max = 500) String storageKey,
            @NotBlank @Size(max = 255) String nomeArquivo,
            @NotBlank @Size(max = 150) @Pattern(regexp = "^[\\w.+-]+/[\\w.+-]+$", message = "tipo MIME inválido")
            String mimeType,
            @PositiveOrZero Long tamanhoBytes
    ) {
    }

    public record AnexoResponse(UUID id, String storageKey, String nomeArquivo, String mimeType, Long tamanhoBytes,
                                Instant criadoEm) {

        public static AnexoResponse de(ExameAnexo a) {
            return new AnexoResponse(a.getId(), a.getStorageKey(), a.getNomeArquivo(), a.getMimeType(),
                    a.getTamanhoBytes(), a.getCriadoEm());
        }
    }

    public record ExameResponse(UUID id, UUID pacienteId, String pacienteNome, UUID consultaId, String nome,
                                String laboratorio, LocalDate dataColeta, String observacao,
                                List<ResultadoExameResponse> resultados, List<AnexoResponse> anexos,
                                Instant criadoEm, Instant atualizadoEm) {

        public static ExameResponse de(Exame e, String pacienteNome, List<ExameAnexo> anexos) {
            return new ExameResponse(e.getId(), e.getPacienteId(), pacienteNome, e.getConsultaId(), e.getNome(),
                    e.getLaboratorio(), e.getDataColeta(), e.getObservacao(),
                    e.getResultados().stream().sorted(Comparator.comparing(r -> r.getMetrica().getId()))
                            .map(ResultadoExameResponse::de).toList(),
                    anexos.stream().map(AnexoResponse::de).toList(),
                    e.getCriadoEm(), e.getAtualizadoEm());
        }
    }

    public record ExameResumoResponse(UUID id, UUID pacienteId, String pacienteNome, UUID consultaId, String nome,
                                      String laboratorio, LocalDate dataColeta) {

        public static ExameResumoResponse de(Exame e, String pacienteNome) {
            return new ExameResumoResponse(e.getId(), e.getPacienteId(), pacienteNome, e.getConsultaId(),
                    e.getNome(), e.getLaboratorio(), e.getDataColeta());
        }
    }

    // ------------------------------------------------------------------ evolução e comparação

    /** Um valor na série: de uma avaliação (data/hora da medição) ou de um exame (data da coleta). */
    public record PontoEvolucao(OffsetDateTime dataHora, String origem, UUID origemId, Integer equipamentoId,
                                BigDecimal valor, BigDecimal refMin, BigDecimal refMax) {
    }

    /** Série de uma métrica numa região (regiao nula = corpo inteiro), em ordem cronológica. */
    public record SerieEvolucao(String metrica, String metricaNome, String unidade, String regiao,
                                List<PontoEvolucao> pontos) {
    }

    public record EvolucaoResponse(UUID pacienteId, List<SerieEvolucao> series, List<MetaResponse> metas) {
    }

    /** {@code diferenca = depois - antes}; nula quando a métrica não foi medida nas duas avaliações. */
    public record ItemComparacao(String metrica, String metricaNome, String unidade, String regiao,
                                 BigDecimal antes, BigDecimal depois, BigDecimal diferenca) {
    }

    public record ComparacaoResponse(UUID pacienteId, AvaliacaoResumoResponse antes, AvaliacaoResumoResponse depois,
                                     List<ItemComparacao> itens) {
    }

    /** {@code GET /internal/consultas/{id}} do ms-consulta. */
    public record ConsultaInterna(UUID id, UUID pacienteId, UUID nutricionistaId, OffsetDateTime dataHora,
                                  String status) {
    }
}
