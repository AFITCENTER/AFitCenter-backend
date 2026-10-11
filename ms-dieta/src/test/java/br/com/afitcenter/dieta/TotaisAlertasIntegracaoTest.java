package br.com.afitcenter.dieta;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Totais de energia e macros contra as metas, e alertas de restrição com o ms-paciente mockado. */
class TotaisAlertasIntegracaoTest extends IntegracaoBase {

    @Test
    void totaisPorRefeicaoEPorDiaComparadosComAsMetas() throws Exception {
        // Macros por porção base: arroz e frango em 100 g, pão em 50 g (1 unidade).
        int arroz = alimentoOficial("Arroz", 1, "100", "128", "2.5", "28.1", "0.2", "1.6");
        int frango = alimentoOficial("Frango", 6, "100", "159", "32", "0", "2.5", null);
        int pao = alimentoOficial("Pão", 1, "50", "150", "4", "29", "1.5", "1.2");
        int unidade = medidaOficial(pao, "Unidade", "50");

        UUID dieta = uuid(postComo(ana, "/v1/dietas", """
                {"pacienteId": "%s", "titulo": "Metas", "kcalMeta": 2000, "proteinaMetaG": 120, "carboidratoMetaG": 250}
                """.formatted(paciente(ana))).andExpect(status().isCreated()));
        UUID almoco = refeicao(ana, dieta, "Almoço", null);
        item(ana, dieta, almoco, arroz, "150", null);                                       // 192 kcal, 3,75 g ptn
        item(ana, dieta, almoco, frango, "120", null);                                      // 190,8 kcal, 38,4 g ptn
        adicionarItem(ana, dieta, almoco, pao, "1", unidade, 2).andExpect(status().isCreated()); // opção 2: fora do total
        UUID lanche = refeicao(ana, dieta, "Lanche", "[6, 7]");
        item(ana, dieta, lanche, pao, "2", unidade);                                       // 300 kcal

        getComo(ana, "/v1/dietas/" + dieta + "/totais")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metas.kcal").value(2000.0))
                .andExpect(jsonPath("$.metas.lipidioG").value(nullValue()))
                .andExpect(jsonPath("$.refeicoes.length()").value(2))
                .andExpect(jsonPath("$.refeicoes[0].nome").value("Almoço"))
                .andExpect(jsonPath("$.refeicoes[0].total.kcal").value(382.8))
                .andExpect(jsonPath("$.refeicoes[0].total.proteinaG").value(42.2))
                .andExpect(jsonPath("$.refeicoes[0].total.carboidratoG").value(42.2))
                .andExpect(jsonPath("$.refeicoes[0].total.lipidioG").value(3.3))
                .andExpect(jsonPath("$.refeicoes[0].total.fibraG").value(2.4))
                .andExpect(jsonPath("$.refeicoes[0].opcoes.length()").value(2))
                .andExpect(jsonPath("$.refeicoes[0].opcoes[1].opcao").value(2))
                .andExpect(jsonPath("$.refeicoes[0].opcoes[1].total.kcal").value(150.0))
                .andExpect(jsonPath("$.refeicoes[1].diasSemana", contains(6, 7)))
                .andExpect(jsonPath("$.refeicoes[1].total.kcal").value(300.0))
                .andExpect(jsonPath("$.dias.length()").value(7))
                // Segunda: só o almoço.
                .andExpect(jsonPath("$.dias[0].diaSemana").value(1))
                .andExpect(jsonPath("$.dias[0].total.kcal").value(382.8))
                .andExpect(jsonPath("$.dias[0].kcal.meta").value(2000.0))
                .andExpect(jsonPath("$.dias[0].kcal.total").value(382.8))
                .andExpect(jsonPath("$.dias[0].kcal.diferenca").value(-1617.2))
                .andExpect(jsonPath("$.dias[0].kcal.percentual").value(19.1))
                .andExpect(jsonPath("$.dias[0].proteinaG.percentual").value(35.1))
                .andExpect(jsonPath("$.dias[0].lipidioG").value(nullValue()))
                // Sábado: almoço + lanche.
                .andExpect(jsonPath("$.dias[5].diaSemana").value(6))
                .andExpect(jsonPath("$.dias[5].total.kcal").value(682.8))
                .andExpect(jsonPath("$.dias[5].kcal.percentual").value(34.1))
                .andExpect(jsonPath("$.dias[5].carboidratoG.total").value(100.2));

        UUID vazia = dieta(ana, paciente(ana));
        getComo(ana, "/v1/dietas/" + vazia + "/totais")
                .andExpect(jsonPath("$.refeicoes.length()").value(0))
                .andExpect(jsonPath("$.dias[0].total.kcal").value(0.0))
                .andExpect(jsonPath("$.dias[0].kcal.percentual").value(0.0));
    }

    @Test
    void alertasPorAlimentoEPorGrupoComAsCondicoesDoMsPaciente() throws Exception {
        int amendoim = alimentoOficial("Amendoim", 15);
        int leite = alimentoOficial("Leite integral", 7);
        int queijo = alimentoOficial("Queijo minas", 7);
        int arroz = alimentoOficial("Arroz", 1);
        int alergia = condicaoNova();
        int intolerancia = condicaoNova();
        int inativa = condicaoNova();
        restricao(alergia, amendoim, null);
        restricao(intolerancia, null, 7);      // Leite e derivados
        restricao(intolerancia, leite, null);  // também pelo alimento: vale o motivo ALIMENTO
        restricao(inativa, arroz, null);

        UUID paciente = paciente(ana);
        UUID dieta = dieta(ana, paciente);
        UUID cafe = refeicao(ana, dieta, "Café", null);
        UUID itemArroz = item(ana, dieta, cafe, arroz, "100", null);
        UUID substituto = uuid(adicionarSubstituto(ana, dieta, cafe, itemArroz, amendoim, "30"));
        UUID itemLeite = item(ana, dieta, cafe, leite, "200", null);
        UUID itemQueijo = item(ana, dieta, cafe, queijo, "30", null);
        when(condicaoClient.condicoesAtivas(paciente)).thenReturn(List.of(intolerancia, alergia, alergia));

        getComo(ana, "/v1/dietas/" + dieta + "/alertas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condicoesAtivas", contains(Math.min(alergia, intolerancia), Math.max(alergia, intolerancia))))
                .andExpect(jsonPath("$.alertas.length()").value(3))
                .andExpect(jsonPath("$.alertas[0].itemId").value(itemArroz.toString()))
                .andExpect(jsonPath("$.alertas[0].substitutoId").value(substituto.toString()))
                .andExpect(jsonPath("$.alertas[0].alimentoId").value(amendoim))
                .andExpect(jsonPath("$.alertas[0].condicaoId").value(alergia))
                .andExpect(jsonPath("$.alertas[0].motivo").value("ALIMENTO"))
                .andExpect(jsonPath("$.alertas[0].refeicao").value("Café"))
                .andExpect(jsonPath("$.alertas[1].itemId").value(itemLeite.toString()))
                .andExpect(jsonPath("$.alertas[1].substitutoId").value(nullValue()))
                .andExpect(jsonPath("$.alertas[1].condicaoId").value(intolerancia))
                .andExpect(jsonPath("$.alertas[1].motivo").value("ALIMENTO"))
                .andExpect(jsonPath("$.alertas[2].itemId").value(itemQueijo.toString()))
                .andExpect(jsonPath("$.alertas[2].motivo").value("GRUPO"))
                .andExpect(jsonPath("$.alertas[2].grupoAlimentar").value("Leite e derivados"));

        when(condicaoClient.condicoesAtivas(paciente)).thenReturn(List.of());
        getComo(ana, "/v1/dietas/" + dieta + "/alertas").andExpect(jsonPath("$.alertas.length()").value(0));

        getComo(bruno, "/v1/dietas/" + dieta + "/alertas").andExpect(status().isNotFound());
    }

    @Test
    void modeloNaoTemAlertasNemConsultaOMsPaciente() throws Exception {
        UUID modelo = modelo(ana, "Modelo");
        getComo(ana, "/v1/dietas/" + modelo + "/alertas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertas.length()").value(0));
        verify(condicaoClient, never()).condicoesAtivas(any());
    }

    private static int condicaoNova() {
        return ThreadLocalRandom.current().nextInt(1_000_000, 2_000_000_000);
    }

    private void restricao(int condicao, Integer alimento, Integer grupo) {
        jdbc.update("INSERT INTO condicao_restricao (condicao_id, alimento_id, grupo_alimentar_id) VALUES (?, ?, ?)",
                condicao, alimento, grupo);
    }
}
