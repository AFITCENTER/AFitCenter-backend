package br.com.afitcenter.dieta.model.dto;

import br.com.afitcenter.dieta.model.entity.Alimento;
import br.com.afitcenter.dieta.model.entity.AlimentoMedida;
import br.com.afitcenter.dieta.model.entity.Dieta;
import br.com.afitcenter.dieta.model.entity.GrupoAlimentar;
import br.com.afitcenter.dieta.model.entity.Nutriente;
import br.com.afitcenter.dieta.model.entity.Refeicao;
import br.com.afitcenter.dieta.model.entity.RefeicaoItem;
import br.com.afitcenter.dieta.model.entity.RefeicaoItemSubstituto;
import br.com.afitcenter.dieta.model.enums.Enums.FonteAlimento;
import br.com.afitcenter.dieta.model.enums.Enums.MotivoRestricao;
import br.com.afitcenter.dieta.model.enums.Enums.StatusDieta;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP do ms-dieta. */
public final class Dtos {

    private static final BigDecimal PORCAO_PADRAO_G = new BigDecimal("100");
    private static final List<Integer> TODOS_OS_DIAS = List.of(1, 2, 3, 4, 5, 6, 7);

    private Dtos() {
    }

    // ------------------------------------------------------------------ catálogos

    public record GrupoResponse(Integer id, String nome) {

        public static GrupoResponse de(GrupoAlimentar g) {
            return new GrupoResponse(g.getId(), g.getNome());
        }
    }

    public record NutrienteResponse(Integer id, String nome, String unidade, BigDecimal vdReferencia) {

        public static NutrienteResponse de(Nutriente n) {
            return new NutrienteResponse(n.getId(), n.getNome(), n.getUnidade(), n.getVdReferencia());
        }
    }

    // ------------------------------------------------------------------ alimentos

    /** Macros e micronutrientes na porção base (100 g se omitida). */
    public record AlimentoRequest(
            @NotBlank @Size(max = 200) String nome,
            Integer grupoId,
            @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 5, fraction = 2) BigDecimal porcaoBaseG,
            @DecimalMin("0.00") @Digits(integer = 5, fraction = 2) BigDecimal kcal,
            @DecimalMin("0.00") @Digits(integer = 5, fraction = 2) BigDecimal proteinaG,
            @DecimalMin("0.00") @Digits(integer = 5, fraction = 2) BigDecimal carboidratoG,
            @DecimalMin("0.00") @Digits(integer = 5, fraction = 2) BigDecimal lipidioG,
            @DecimalMin("0.00") @Digits(integer = 5, fraction = 2) BigDecimal fibraG,
            @Size(max = 100) List<@Valid @NotNull NutrienteQuantidade> nutrientes
    ) {

        public BigDecimal porcaoOuPadrao() {
            return porcaoBaseG == null ? PORCAO_PADRAO_G : porcaoBaseG;
        }

        public List<NutrienteQuantidade> nutrientesOuVazio() {
            return nutrientes == null ? List.of() : nutrientes;
        }
    }

    public record NutrienteQuantidade(
            @NotNull Integer nutrienteId,
            @NotNull @DecimalMin("0.000") @Digits(integer = 7, fraction = 3) BigDecimal quantidade
    ) {
    }

    /** Item da busca de alimentos. */
    public record AlimentoResponse(Integer id, String nome, Integer grupoId, String grupo, FonteAlimento fonte,
                                   boolean proprio, BigDecimal porcaoBaseG, BigDecimal kcal, BigDecimal proteinaG,
                                   BigDecimal carboidratoG, BigDecimal lipidioG, BigDecimal fibraG) {

        public static AlimentoResponse de(Alimento a) {
            return new AlimentoResponse(a.getId(), a.getNome(),
                    a.getGrupo() == null ? null : a.getGrupo().getId(),
                    a.getGrupo() == null ? null : a.getGrupo().getNome(),
                    a.getFonte(), a.proprio(), a.getPorcaoBaseG(), a.getKcal(), a.getProteinaG(),
                    a.getCarboidratoG(), a.getLipidioG(), a.getFibraG());
        }
    }

    /** Alimento com medidas caseiras e micronutrientes. */
    public record AlimentoDetalheResponse(
            @JsonUnwrapped AlimentoResponse alimento,
            String codigoFonte,
            List<MedidaResponse> medidas,
            List<NutrienteAlimentoResponse> nutrientes,
            Instant criadoEm,
            Instant atualizadoEm
    ) {

        public static AlimentoDetalheResponse de(Alimento a, List<MedidaResponse> medidas,
                                                 List<NutrienteAlimentoResponse> nutrientes) {
            return new AlimentoDetalheResponse(AlimentoResponse.de(a), a.getCodigoFonte(), medidas, nutrientes,
                    a.getCriadoEm(), a.getAtualizadoEm());
        }
    }

    public record NutrienteAlimentoResponse(Integer nutrienteId, String nome, String unidade, BigDecimal quantidade) {
    }

    public record MedidaRequest(
            @NotBlank @Size(max = 100) String descricao,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 5, fraction = 2) BigDecimal gramas
    ) {
    }

    public record MedidaResponse(Integer id, Integer alimentoId, String descricao, BigDecimal gramas) {

        public static MedidaResponse de(AlimentoMedida m) {
            return new MedidaResponse(m.getId(), m.getAlimentoId(), m.getDescricao(), m.getGramas());
        }
    }

    // ------------------------------------------------------------------ dietas

    /**
     * Dieta de paciente ({@code pacienteId}) ou modelo ({@code modelo = true}, sem paciente nem consulta).
     * Paciente e "modelo" não mudam depois de criados.
     */
    public record DietaRequest(
            UUID pacienteId,
            boolean modelo,
            UUID consultaId,
            @NotBlank @Size(max = 200) String titulo,
            @Size(max = 2000) String objetivo,
            @DecimalMin("0.0") @Digits(integer = 6, fraction = 1) BigDecimal kcalMeta,
            @DecimalMin("0.0") @Digits(integer = 5, fraction = 1) BigDecimal proteinaMetaG,
            @DecimalMin("0.0") @Digits(integer = 5, fraction = 1) BigDecimal carboidratoMetaG,
            @DecimalMin("0.0") @Digits(integer = 5, fraction = 1) BigDecimal lipidioMetaG,
            LocalDate dataInicio,
            LocalDate dataFim,
            @Size(max = 4000) String observacao
    ) {
    }

    public record StatusRequest(@NotNull StatusDieta status) {
    }

    public record AplicarModeloRequest(@NotNull UUID pacienteId, UUID consultaId) {
    }

    public record DietaResponse(
            UUID id,
            boolean modelo,
            UUID pacienteId,
            String pacienteNome,
            UUID consultaId,
            UUID dietaOrigemId,
            String titulo,
            String objetivo,
            BigDecimal kcalMeta,
            BigDecimal proteinaMetaG,
            BigDecimal carboidratoMetaG,
            BigDecimal lipidioMetaG,
            LocalDate dataInicio,
            LocalDate dataFim,
            StatusDieta status,
            String observacao,
            Instant criadoEm,
            Instant atualizadoEm
    ) {

        public static DietaResponse de(Dieta d, String pacienteNome) {
            return new DietaResponse(d.getId(), d.isModelo(), d.getPacienteId(), pacienteNome, d.getConsultaId(),
                    d.getDietaOrigemId(), d.getTitulo(), d.getObjetivo(), d.getKcalMeta(), d.getProteinaMetaG(),
                    d.getCarboidratoMetaG(), d.getLipidioMetaG(), d.getDataInicio(), d.getDataFim(), d.getStatus(),
                    d.getObservacao(), d.getCriadoEm(), d.getAtualizadoEm());
        }
    }

    /** Dieta com refeições, itens e substitutos. */
    public record DietaDetalheResponse(@JsonUnwrapped DietaResponse dieta, List<RefeicaoResponse> refeicoes) {
    }

    // ------------------------------------------------------------------ refeições, itens e substitutos

    /** {@code diasSemana} ISO (1 = segunda ... 7 = domingo); omitido = todos. {@code ordem} omitida = no fim. */
    public record RefeicaoRequest(
            @NotBlank @Size(max = 100) String nome,
            LocalTime horario,
            @Min(1) @Max(1000) Integer ordem,
            @Size(min = 1, max = 7) List<@NotNull @Min(1) @Max(7) Integer> diasSemana,
            @Size(max = 2000) String observacao
    ) {

        public List<Integer> diasOuTodos() {
            return diasSemana == null ? TODOS_OS_DIAS : diasSemana;
        }
    }

    public record RefeicaoResponse(UUID id, String nome, LocalTime horario, Integer ordem, List<Integer> diasSemana,
                                   String observacao, List<ItemResponse> itens) {

        public static RefeicaoResponse de(Refeicao r, List<ItemResponse> itens) {
            return new RefeicaoResponse(r.getId(), r.getNome(), r.getHorario(), r.getOrdem(), r.getDiasSemana(),
                    r.getObservacao(), itens);
        }
    }

    /**
     * {@code quantidade} na medida caseira escolhida; sem {@code alimentoMedidaId}, em gramas. Itens com a
     * mesma {@code opcao} formam uma opção da refeição (1 = principal).
     */
    public record ItemRequest(
            @NotNull Integer alimentoId,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 5, fraction = 2) BigDecimal quantidade,
            Integer alimentoMedidaId,
            @Min(1) @Max(20) Integer opcao,
            @Min(1) @Max(1000) Integer ordem,
            @Size(max = 1000) String observacao
    ) {

        public int opcaoOuPrincipal() {
            return opcao == null ? 1 : opcao;
        }
    }

    public record SubstitutoRequest(
            @NotNull Integer alimentoId,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 5, fraction = 2) BigDecimal quantidade,
            Integer alimentoMedidaId
    ) {
    }

    public record ItemResponse(UUID id, Integer opcao, Integer ordem, Integer alimentoId, String alimentoNome,
                               BigDecimal quantidade, Integer alimentoMedidaId, String medida, BigDecimal quantidadeG,
                               String observacao, Macros macros, List<SubstitutoResponse> substitutos) {

        public static ItemResponse de(RefeicaoItem i, Alimento alimento, AlimentoMedida medida, Macros macros,
                                      List<SubstitutoResponse> substitutos) {
            return new ItemResponse(i.getId(), i.getOpcao(), i.getOrdem(), i.getAlimentoId(), alimento.getNome(),
                    i.getQuantidade(), i.getAlimentoMedidaId(), medida == null ? null : medida.getDescricao(),
                    i.getQuantidadeG(), i.getObservacao(), macros, substitutos);
        }
    }

    public record SubstitutoResponse(UUID id, Integer alimentoId, String alimentoNome, BigDecimal quantidade,
                                     Integer alimentoMedidaId, String medida, BigDecimal quantidadeG, Macros macros) {

        public static SubstitutoResponse de(RefeicaoItemSubstituto s, Alimento alimento, AlimentoMedida medida,
                                            Macros macros) {
            return new SubstitutoResponse(s.getId(), s.getAlimentoId(), alimento.getNome(), s.getQuantidade(),
                    s.getAlimentoMedidaId(), medida == null ? null : medida.getDescricao(), s.getQuantidadeG(), macros);
        }
    }

    // ------------------------------------------------------------------ totais e alertas

    /** Energia (kcal) e macros (g). Nas respostas, com uma casa decimal. */
    public record Macros(BigDecimal kcal, BigDecimal proteinaG, BigDecimal carboidratoG, BigDecimal lipidioG,
                         BigDecimal fibraG) {

        public static final Macros ZERO =
                new Macros(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        public Macros mais(Macros outro) {
            return new Macros(kcal.add(outro.kcal), proteinaG.add(outro.proteinaG),
                    carboidratoG.add(outro.carboidratoG), lipidioG.add(outro.lipidioG), fibraG.add(outro.fibraG));
        }

        public Macros arredondado() {
            return new Macros(uma(kcal), uma(proteinaG), uma(carboidratoG), uma(lipidioG), uma(fibraG));
        }

        private static BigDecimal uma(BigDecimal valor) {
            return valor.setScale(1, RoundingMode.HALF_UP);
        }
    }

    /** Metas diárias da dieta (nulas quando não definidas). */
    public record Metas(BigDecimal kcal, BigDecimal proteinaG, BigDecimal carboidratoG, BigDecimal lipidioG) {

        public static Metas de(Dieta d) {
            return new Metas(d.getKcalMeta(), d.getProteinaMetaG(), d.getCarboidratoMetaG(), d.getLipidioMetaG());
        }
    }

    /** Total do dia contra a meta: {@code diferenca = total - meta}; {@code percentual = total / meta × 100}. */
    public record ComparacaoMeta(BigDecimal meta, BigDecimal total, BigDecimal diferenca, BigDecimal percentual) {
    }

    public record TotalOpcao(int opcao, Macros total) {
    }

    /** {@code total} é a opção 1 (principal); {@code opcoes} traz cada opção separada. */
    public record TotalRefeicao(UUID refeicaoId, String nome, LocalTime horario, List<Integer> diasSemana,
                                Macros total, List<TotalOpcao> opcoes) {
    }

    /** Soma das refeições servidas no dia (opção 1 de cada uma), comparada com as metas. */
    public record TotalDia(int diaSemana, Macros total, ComparacaoMeta kcal, ComparacaoMeta proteinaG,
                           ComparacaoMeta carboidratoG, ComparacaoMeta lipidioG) {
    }

    public record TotaisResponse(UUID dietaId, Metas metas, List<TotalRefeicao> refeicoes, List<TotalDia> dias) {
    }

    /** Item ({@code substitutoId} nulo) ou substituto que usa alimento restrito por uma condição ativa do paciente. */
    public record AlertaResponse(UUID refeicaoId, String refeicao, UUID itemId, UUID substitutoId, Integer alimentoId,
                                 String alimento, Integer grupoAlimentarId, String grupoAlimentar, Integer condicaoId,
                                 MotivoRestricao motivo) {
    }

    /** {@code condicoesAtivas}: ids do catálogo de condições do ms-paciente que foram cruzados. */
    public record AlertasResponse(UUID dietaId, List<Integer> condicoesAtivas, List<AlertaResponse> alertas) {
    }
}
