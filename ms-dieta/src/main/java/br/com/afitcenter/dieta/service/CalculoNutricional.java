package br.com.afitcenter.dieta.service;

import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.dieta.model.dto.Dtos.ComparacaoMeta;
import br.com.afitcenter.dieta.model.dto.Dtos.Macros;
import br.com.afitcenter.dieta.model.entity.Alimento;
import br.com.afitcenter.dieta.model.entity.AlimentoMedida;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Contas da dieta. Somas em precisão cheia; o arredondamento fica para a resposta. */
final class CalculoNutricional {

    /** Limite de {@code quantidade_g numeric(8,2)}. */
    private static final BigDecimal LIMITE_GRAMAS = new BigDecimal("999999.99");
    private static final BigDecimal CEM = new BigDecimal("100");

    private CalculoNutricional() {
    }

    /** Gramas da medida × quantidade, ou a própria quantidade (em gramas) quando não há medida. */
    static BigDecimal gramas(BigDecimal quantidade, AlimentoMedida medida) {
        BigDecimal gramas = (medida == null ? quantidade : medida.getGramas().multiply(quantidade))
                .setScale(2, RoundingMode.HALF_UP);
        if (gramas.signum() <= 0)
            throw new RegraNegocioException("A quantidade em gramas precisa ser maior que zero.");
        if (gramas.compareTo(LIMITE_GRAMAS) > 0)
            throw new RegraNegocioException("A quantidade passa do limite de " + LIMITE_GRAMAS.toPlainString() + " g.");
        return gramas;
    }

    /** Macro do alimento × quantidade_g / porcao_base_g. Macro não informado conta como zero. */
    static Macros macros(Alimento alimento, BigDecimal quantidadeG) {
        BigDecimal fator = quantidadeG.divide(alimento.getPorcaoBaseG(), MathContext.DECIMAL64);
        return new Macros(vezes(alimento.getKcal(), fator), vezes(alimento.getProteinaG(), fator),
                vezes(alimento.getCarboidratoG(), fator), vezes(alimento.getLipidioG(), fator),
                vezes(alimento.getFibraG(), fator));
    }

    /** {@code null} quando a dieta não tem a meta. */
    static ComparacaoMeta comparar(BigDecimal meta, BigDecimal total) {
        if (meta == null) return null;
        BigDecimal totalArredondado = total.setScale(1, RoundingMode.HALF_UP);
        BigDecimal percentual = meta.signum() == 0 ? null
                : total.multiply(CEM).divide(meta, 1, RoundingMode.HALF_UP);
        return new ComparacaoMeta(meta, totalArredondado, totalArredondado.subtract(meta), percentual);
    }

    private static BigDecimal vezes(BigDecimal valor, BigDecimal fator) {
        return valor == null ? BigDecimal.ZERO : valor.multiply(fator, MathContext.DECIMAL64);
    }
}
