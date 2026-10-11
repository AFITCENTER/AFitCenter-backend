package br.com.afitcenter.avaliacao;

import br.com.afitcenter.core.teste.TokensTeste;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Valida a base do ms-avaliacao: catálogos, réplica do paciente e o esquema (IMC gerado, FKs
 * compostas e a view de evolução) com os valores do laudo de bioimpedância usado como referência.
 */
class AvaliacaoIntegracaoTest extends IntegracaoBase {

    @Test
    void catalogosDeMetricasERegioes() throws Exception {
        String token = TokensTeste.nutricionista(ana);
        mvc.perform(get("/v1/metricas").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'GORDURA_PCT')].unidade").value("%"));
        mvc.perform(get("/v1/metricas").param("categoria", "impedancia").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get("/v1/regioes-corporais").param("tipo", "SEGMENTO").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/v1/metricas")).andExpect(status().isUnauthorized());
    }

    @Test
    void laudoDeBioimpedanciaNaSerieDeEvolucao() {
        UUID paciente = paciente(ana);
        UUID anterior = inserirAvaliacao(ana, paciente, "2026-10-01T07:30:00-03:00", "108.9");
        UUID atual = inserirAvaliacao(ana, paciente, "2026-10-08T07:26:00-03:00", "108.4");
        inserirResultado(anterior, "GORDURA_PCT", null, "36.5");
        inserirResultado(atual, "GORDURA_PCT", null, "36.6");
        inserirResultado(atual, "GORDURA_SEG_KG", "TRONCO", "21.9");
        inserirResultado(atual, "IMPEDANCIA_20KHZ", "BRACO_DIR", "263.4");

        assertThat(jdbc.queryForObject("SELECT imc FROM avaliacao WHERE id = ?", BigDecimal.class, atual))
                .isEqualByComparingTo("36.22");

        List<Map<String, Object>> serie = jdbc.queryForList("""
                SELECT metrica, valor FROM vw_evolucao
                WHERE nutricionista_id = ? AND paciente_id = ? AND metrica IN ('PESO', 'GORDURA_PCT') AND regiao IS NULL
                ORDER BY metrica, data_hora""", ana, paciente);
        assertThat(serie).extracting(l -> l.get("metrica") + "=" + ((BigDecimal) l.get("valor")).stripTrailingZeros().toPlainString())
                .containsExactly("GORDURA_PCT=36.5", "GORDURA_PCT=36.6", "PESO=108.9", "PESO=108.4");

        assertThatThrownBy(() -> inserirResultado(atual, "GORDURA_SEG_KG", "TRONCO", "1"))
                .as("mesma métrica e região duas vezes no mesmo laudo")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoRecusaAvaliacaoDePacienteDeOutroNutricionista() {
        UUID pacienteDaAna = paciente(ana);
        assertThatThrownBy(() -> inserirAvaliacao(bruno, pacienteDaAna, "2026-10-08T07:26:00-03:00", "80"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID inserirAvaliacao(UUID nutricionista, UUID paciente, String dataHora, String peso) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO avaliacao (id, nutricionista_id, paciente_id, metodo, data_hora_medicao, peso_kg, altura_cm)
                VALUES (?, ?, ?, 'BIOIMPEDANCIA', ?, ?, 173)""",
                id, nutricionista, paciente, OffsetDateTime.parse(dataHora), new BigDecimal(peso));
        return id;
    }

    private void inserirResultado(UUID avaliacao, String metrica, String regiao, String valor) {
        jdbc.update("""
                INSERT INTO avaliacao_resultado (id, avaliacao_id, metrica_id, regiao_id, valor)
                SELECT ?, ?, m.id, r.id, ? FROM metrica m LEFT JOIN regiao_corporal r ON r.codigo = ?
                WHERE m.codigo = ?""", UUID.randomUUID(), avaliacao, new BigDecimal(valor), regiao, metrica);
    }
}
