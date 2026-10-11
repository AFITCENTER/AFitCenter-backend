package br.com.afitcenter.avaliacao;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Evolução (vw_evolucao filtrada pelo nutricionista, com metas ativas) e comparação entre avaliações. */
class EvolucaoApiTest extends IntegracaoBase {

    @Test
    void evolucaoPorMetricaERegiaoComMetasAtivas() throws Exception {
        UUID paciente = paciente(ana);
        UUID anterior = idDe(enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-01T07:30:00-03:00", "108.9",
                resultado("GORDURA_PCT", null, "36.5"),
                resultado("GORDURA_SEG_KG", "TRONCO", "22.3"),
                resultado("GORDURA_SEG_KG", "BRACO_DIR", "2.9"))).andExpect(status().isCreated()));
        UUID atual = idDe(enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "108.4",
                resultado("GORDURA_PCT", null, "36.6"),
                resultado("GORDURA_SEG_KG", "TRONCO", "21.9"))).andExpect(status().isCreated()));
        enviar(ana, post("/v1/exames"), exame(paciente, "Glicemia", "2026-10-05", resultadoExame("GLICEMIA_JEJUM", "101.5")))
                .andExpect(status().isCreated());

        String metas = "/v1/pacientes/" + paciente + "/metas";
        enviar(ana, post(metas), meta("GLICEMIA_JEJUM", "90", null, null)).andExpect(status().isCreated());
        enviar(ana, post(metas), meta("PESO", "95", null, "2027-03-31")).andExpect(status().isCreated());
        enviar(ana, post(metas), meta("GORDURA_PCT", "30", null, null).put("status", "CANCELADA"))
                .andExpect(status().isCreated());
        enviar(ana, post(metas), meta("HBA1C", "5.4", null, null)).andExpect(status().isCreated());

        // Dados de outro nutricionista nunca aparecem, mesmo com a mesma métrica.
        UUID doBruno = paciente(bruno);
        enviar(bruno, post("/v1/avaliacoes"), avaliacao(doBruno, "2026-10-05T07:00:00-03:00", "70.0"))
                .andExpect(status().isCreated());

        String url = "/v1/pacientes/" + paciente + "/evolucao";
        chamar(ana, get(url).param("metricas", "GLICEMIA_JEJUM,gordura_pct,PESO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series.length()").value(3))
                .andExpect(jsonPath("$.series[0].metrica").value("PESO"))
                .andExpect(jsonPath("$.series[0].unidade").value("kg"))
                .andExpect(jsonPath("$.series[0].regiao").value(nullValue()))
                .andExpect(jsonPath("$.series[0].pontos.length()").value(2))
                .andExpect(jsonPath("$.series[0].pontos[0].valor").value(108.9))
                .andExpect(jsonPath("$.series[0].pontos[1].valor").value(108.4))
                .andExpect(jsonPath("$.series[0].pontos[1].origem").value("AVALIACAO"))
                .andExpect(jsonPath("$.series[0].pontos[1].origemId").value(atual.toString()))
                .andExpect(jsonPath("$.series[1].metrica").value("GORDURA_PCT"))
                .andExpect(jsonPath("$.series[1].pontos[0].valor").value(36.5))
                .andExpect(jsonPath("$.series[2].metrica").value("GLICEMIA_JEJUM"))
                .andExpect(jsonPath("$.series[2].pontos[0].origem").value("EXAME"))
                .andExpect(jsonPath("$.series[2].pontos[0].valor").value(101.5))
                // Só as metas ATIVAS das métricas pedidas, na ordem do catálogo.
                .andExpect(jsonPath("$.metas.length()").value(2))
                .andExpect(jsonPath("$.metas[0].metricaCodigo").value("PESO"))
                .andExpect(jsonPath("$.metas[1].metricaCodigo").value("GLICEMIA_JEJUM"));

        chamar(ana, get(url).param("metricas", "GORDURA_SEG_KG"))
                .andExpect(jsonPath("$.series.length()").value(2))
                .andExpect(jsonPath("$.series[0].regiao").value("BRACO_DIR"))
                .andExpect(jsonPath("$.series[0].pontos.length()").value(1))
                .andExpect(jsonPath("$.series[1].regiao").value("TRONCO"))
                .andExpect(jsonPath("$.series[1].pontos.length()").value(2))
                .andExpect(jsonPath("$.metas.length()").value(0));
        chamar(ana, get(url).param("metricas", "GORDURA_SEG_KG").param("regiao", "tronco"))
                .andExpect(jsonPath("$.series.length()").value(1))
                .andExpect(jsonPath("$.series[0].regiao").value("TRONCO"))
                .andExpect(jsonPath("$.series[0].pontos[1].valor").value(21.9));
        chamar(ana, get(url).param("metricas", "PESO").param("inicio", "2026-10-02T00:00:00-03:00"))
                .andExpect(jsonPath("$.series[0].pontos.length()").value(1))
                .andExpect(jsonPath("$.series[0].pontos[0].origemId").value(atual.toString()));
        chamar(ana, get(url).param("metricas", "PESO").param("fim", "2026-09-01T00:00:00-03:00"))
                .andExpect(jsonPath("$.series.length()").value(0));

        // Avaliação excluída sai da série.
        chamar(ana, delete("/v1/avaliacoes/" + anterior)).andExpect(status().isNoContent());
        chamar(ana, get(url).param("metricas", "PESO"))
                .andExpect(jsonPath("$.series[0].pontos.length()").value(1));

        chamar(ana, get(url).param("metricas", "PESO,NAO_EXISTE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Métrica desconhecida: NAO_EXISTE."));
        chamar(ana, get(url).param("metricas", "PESO").param("regiao", "OMBRO"))
                .andExpect(status().isUnprocessableEntity());
        chamar(ana, get(url)).andExpect(status().isBadRequest());
        chamar(bruno, get(url).param("metricas", "PESO")).andExpect(status().isNotFound());
    }

    @Test
    void comparacaoEntreDuasAvaliacoes() throws Exception {
        UUID paciente = paciente(ana);
        UUID anterior = idDe(enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-01T07:30:00-03:00", "108.9",
                resultado("GORDURA_PCT", null, "36.5"),
                resultado("GORDURA_SEG_KG", "BRACO_DIR", "2.9"))));
        UUID atual = idDe(enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "108.4",
                resultado("GORDURA_PCT", null, "36.6"),
                resultado("GORDURA_SEG_KG", "TRONCO", "21.9"))));

        String url = "/v1/pacientes/" + paciente + "/comparacao";
        // Ordem invertida de propósito: "antes" é sempre a medição mais antiga.
        chamar(ana, get(url).param("a", atual.toString()).param("b", anterior.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antes.id").value(anterior.toString()))
                .andExpect(jsonPath("$.depois.id").value(atual.toString()))
                .andExpect(jsonPath("$.depois.imc").value(36.22))
                .andExpect(jsonPath("$.itens.length()").value(6))
                .andExpect(jsonPath("$.itens[0].metrica").value("PESO"))
                .andExpect(jsonPath("$.itens[0].antes").value(108.9))
                .andExpect(jsonPath("$.itens[0].depois").value(108.4))
                .andExpect(jsonPath("$.itens[0].diferenca").value(-0.5))
                .andExpect(jsonPath("$.itens[1].metrica").value("ALTURA"))
                .andExpect(jsonPath("$.itens[1].diferenca").value(0.0))
                .andExpect(jsonPath("$.itens[2].metrica").value("IMC"))
                .andExpect(jsonPath("$.itens[2].diferenca").value(-0.17))
                .andExpect(jsonPath("$.itens[3].metrica").value("GORDURA_PCT"))
                .andExpect(jsonPath("$.itens[3].diferenca").value(0.1))
                .andExpect(jsonPath("$.itens[4].metrica").value("GORDURA_SEG_KG"))
                .andExpect(jsonPath("$.itens[4].regiao").value("BRACO_DIR"))
                .andExpect(jsonPath("$.itens[4].antes").value(2.9))
                .andExpect(jsonPath("$.itens[4].depois").value(nullValue()))
                .andExpect(jsonPath("$.itens[4].diferenca").value(nullValue()))
                .andExpect(jsonPath("$.itens[5].regiao").value("TRONCO"))
                .andExpect(jsonPath("$.itens[5].antes").value(nullValue()))
                .andExpect(jsonPath("$.itens[5].depois").value(21.9));

        chamar(ana, get(url).param("a", atual.toString()).param("b", atual.toString()))
                .andExpect(status().isUnprocessableEntity());
        UUID deOutroPaciente = idDe(enviar(ana, post("/v1/avaliacoes"),
                avaliacao(paciente(ana), "2026-10-08T07:26:00-03:00", "70.0")));
        chamar(ana, get(url).param("a", anterior.toString()).param("b", deOutroPaciente.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Avaliação não encontrada."));
        chamar(bruno, get(url).param("a", anterior.toString()).param("b", atual.toString()))
                .andExpect(status().isNotFound());

        chamar(ana, delete("/v1/avaliacoes/" + anterior)).andExpect(status().isNoContent());
        chamar(ana, get(url).param("a", atual.toString()).param("b", anterior.toString()))
                .andExpect(status().isNotFound());
    }
}
