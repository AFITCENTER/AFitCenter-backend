package br.com.afitcenter.dieta;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alimentos próprios (CRUD, micronutrientes, isolamento) e medidas caseiras. */
class AlimentoIntegracaoTest extends IntegracaoBase {

    @Test
    void alimentoProprioComNutrientesSoParaODono() throws Exception {
        String nome = "Panqueca fit " + sufixo;
        String corpo = """
                {"nome": "%s", "grupoId": 13, "porcaoBaseG": 50, "kcal": 120, "proteinaG": 8, "carboidratoG": 15,
                 "lipidioG": 3, "nutrientes": [{"nutrienteId": 6, "quantidade": 1.2}, {"nutrienteId": 2, "quantidade": 45.5}]}
                """.formatted(nome);
        int id = Integer.parseInt(id(postComo(ana, "/v1/alimentos", corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value(nome))
                .andExpect(jsonPath("$.fonte").value("PROPRIO"))
                .andExpect(jsonPath("$.proprio").value(true))
                .andExpect(jsonPath("$.grupo").value("Alimentos preparados"))
                .andExpect(jsonPath("$.porcaoBaseG").value(50.0))
                .andExpect(jsonPath("$.nutrientes.length()").value(2))
                .andExpect(jsonPath("$.nutrientes[0].nome").value("Cálcio"))
                .andExpect(jsonPath("$.nutrientes[0].quantidade").value(45.5))
                .andExpect(jsonPath("$.medidas.length()").value(0))));

        postComo(ana, "/v1/alimentos", corpo.replace(nome, nome.toUpperCase()))
                .andExpect(status().isConflict());
        // O nome só é único entre os próprios de cada nutricionista.
        postComo(bruno, "/v1/alimentos", corpo)
                .andExpect(status().isCreated());

        getComo(ana, "/v1/alimentos").andExpect(status().isOk());
        getComo(ana, "/v1/alimentos?proprios=true&busca=" + sufixo)
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(id));

        String url = "/v1/alimentos/" + id;
        getComo(bruno, url).andExpect(status().isNotFound());
        putComo(bruno, url, corpo).andExpect(status().isNotFound());
        deleteComo(bruno, url).andExpect(status().isNotFound());

        putComo(ana, url, """
                {"nome": "%s v2", "kcal": 130, "nutrientes": [{"nutrienteId": 7, "quantidade": 300}]}""".formatted(nome))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value(nome + " v2"))
                .andExpect(jsonPath("$.porcaoBaseG").value(100.0))
                .andExpect(jsonPath("$.grupoId").isEmpty())
                .andExpect(jsonPath("$.nutrientes.length()").value(1))
                .andExpect(jsonPath("$.nutrientes[0].nome").value("Sódio"));
        putComo(ana, url, """
                {"nome": "x", "nutrientes": [{"nutrienteId": 999, "quantidade": 1}]}""")
                .andExpect(status().isNotFound());
        putComo(ana, url, """
                {"nome": "x", "nutrientes": [{"nutrienteId": 7, "quantidade": 1}, {"nutrienteId": 7, "quantidade": 2}]}""")
                .andExpect(status().isUnprocessableEntity());
        putComo(ana, url, """
                {"nome": "x", "porcaoBaseG": 0}""")
                .andExpect(status().isBadRequest());

        deleteComo(ana, url).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT ativo FROM alimento WHERE id = ?", Boolean.class, id))
                .as("exclusão lógica").isFalse();
        getComo(ana, url).andExpect(status().isNotFound());
        getComo(ana, "/v1/alimentos?proprios=true&busca=" + sufixo).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void baseOficialESomenteLeitura() throws Exception {
        int oficial = alimentoOficial("Arroz oficial", 1);
        String url = "/v1/alimentos/" + oficial;

        getComo(ana, url).andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("TACO"))
                .andExpect(jsonPath("$.proprio").value(false));
        putComo(ana, url, "{\"nome\": \"Meu arroz\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A base oficial (TACO/TBCA) é somente leitura. Cadastre um alimento próprio."));
        deleteComo(ana, url).andExpect(status().isUnprocessableEntity());
        postComo(ana, url + "/medidas", "{\"descricao\": \"Colher\", \"gramas\": 25}")
                .andExpect(status().isUnprocessableEntity());

        getComo(ana, "/v1/alimentos?proprios=false&busca=" + sufixo).andExpect(jsonPath("$.totalElementos").value(1));
        getComo(ana, "/v1/nutrientes").andExpect(jsonPath("$.length()").value(16));
    }

    @Test
    void medidasCaseirasDosAlimentosProprios() throws Exception {
        int alimento = alimentoProprio(ana, "Pão caseiro");
        String medidas = "/v1/alimentos/" + alimento + "/medidas";

        int fatia = Integer.parseInt(id(postComo(ana, medidas, "{\"descricao\": \"Fatia fina\", \"gramas\": 30}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alimentoId").value(alimento))
                .andExpect(jsonPath("$.gramas").value(30.0))));
        postComo(ana, medidas, "{\"descricao\": \" fatia FINA \", \"gramas\": 31}").andExpect(status().isConflict());
        postComo(ana, medidas, "{\"descricao\": \"Unidade\", \"gramas\": 0}").andExpect(status().isBadRequest());
        int unidade = Integer.parseInt(id(postComo(ana, medidas, "{\"descricao\": \"Unidade\", \"gramas\": 350}")
                .andExpect(status().isCreated())));

        getComo(ana, medidas).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].descricao").value("Fatia fina"));
        getComo(ana, "/v1/alimentos/" + alimento).andExpect(jsonPath("$.medidas.length()").value(2));
        getComo(bruno, medidas).andExpect(status().isNotFound());
        postComo(bruno, medidas, "{\"descricao\": \"Outra\", \"gramas\": 10}").andExpect(status().isNotFound());
        deleteComo(bruno, medidas + "/" + fatia).andExpect(status().isNotFound());

        putComo(ana, medidas + "/" + fatia, "{\"descricao\": \"Fatia grossa\", \"gramas\": 45}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Fatia grossa"))
                .andExpect(jsonPath("$.gramas").value(45.0));
        putComo(ana, medidas + "/" + fatia, "{\"descricao\": \"Unidade\", \"gramas\": 45}").andExpect(status().isConflict());

        // Em uso num item de dieta: não pode ser excluída.
        UUID dieta = dieta(ana, paciente(ana));
        UUID refeicao = refeicao(ana, dieta, "Café", null);
        item(ana, dieta, refeicao, alimento, "2", fatia);
        deleteComo(ana, medidas + "/" + fatia).andExpect(status().isConflict());

        deleteComo(ana, medidas + "/" + unidade).andExpect(status().isNoContent());
        getComo(ana, medidas).andExpect(jsonPath("$.length()").value(1));
        deleteComo(ana, medidas + "/" + unidade).andExpect(status().isNotFound());
    }
}
