package br.com.afitcenter.dieta;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cópias profundas: modelo aplicado ao paciente e nova versão da dieta. */
class ModeloVersaoIntegracaoTest extends IntegracaoBase {

    @Test
    void aplicarModeloCopiaTudoSemAlterarOModelo() throws Exception {
        int arroz = alimentoOficial("Arroz", 1);
        int colher = medidaOficial(arroz, "Colher", "25");
        int feijao = alimentoOficial("Feijão", 14);
        UUID modelo = modelo(ana, "Low carb");
        UUID cafe = refeicao(ana, modelo, "Café", "[1, 2, 3, 4, 5]");
        UUID itemModelo = item(ana, modelo, cafe, arroz, "4", colher);
        adicionarSubstituto(ana, modelo, cafe, itemModelo, feijao, "60").andExpect(status().isCreated());
        UUID almoco = refeicao(ana, modelo, "Almoço", null);
        item(ana, modelo, almoco, feijao, "150", null);
        UUID paciente = paciente(ana, "Bia", "ATIVO");

        String aplicar = "/v1/modelos/" + modelo + "/aplicar";
        UUID copia = uuid(postComo(ana, aplicar, "{\"pacienteId\": \"%s\"}".formatted(paciente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modelo").value(false))
                .andExpect(jsonPath("$.pacienteId").value(paciente.toString()))
                .andExpect(jsonPath("$.pacienteNome").value("Bia"))
                .andExpect(jsonPath("$.dietaOrigemId").value(modelo.toString()))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.titulo").value("Low carb " + sufixo))
                .andExpect(jsonPath("$.refeicoes.length()").value(2))
                .andExpect(jsonPath("$.refeicoes[0].id").value(not(cafe.toString())))
                .andExpect(jsonPath("$.refeicoes[0].diasSemana.length()").value(5))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].id").value(not(itemModelo.toString())))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].quantidadeG").value(100.0))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].medida").value("Colher"))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].substitutos[0].quantidadeG").value(60.0))
                .andExpect(jsonPath("$.refeicoes[1].itens.length()").value(1)));
        assertThat(contar("refeicao_item_substituto s JOIN refeicao_item i ON i.id = s.refeicao_item_id "
                + "JOIN refeicao r ON r.id = i.refeicao_id WHERE r.dieta_id = ?", copia)).isEqualTo(1);

        // Alterar a cópia não mexe no modelo.
        UUID cafeDaCopia = UUID.fromString(json.readTree(getComo(ana, "/v1/dietas/" + copia).andReturn().getResponse()
                .getContentAsString()).at("/refeicoes/0/id").asText());
        deleteComo(ana, "/v1/dietas/" + copia + "/refeicoes/" + cafeDaCopia).andExpect(status().isNoContent());
        getComo(ana, "/v1/dietas/" + modelo).andExpect(jsonPath("$.refeicoes.length()").value(2))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].substitutos.length()").value(1));
        getComo(ana, "/v1/dietas?pacienteId=" + paciente).andExpect(jsonPath("$.totalElementos").value(1));

        postComo(ana, aplicar, "{\"pacienteId\": \"%s\", \"consultaId\": \"%s\"}".formatted(paciente, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Consulta não encontrada."));
        postComo(bruno, aplicar, "{\"pacienteId\": \"%s\"}".formatted(paciente(bruno)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Modelo não encontrado."));
        postComo(ana, aplicar, "{\"pacienteId\": \"%s\"}".formatted(paciente(bruno)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Paciente não encontrado."));
        postComo(ana, "/v1/modelos/" + copia + "/aplicar", "{\"pacienteId\": \"%s\"}".formatted(paciente))
                .andExpect(status().isNotFound());
        postComo(ana, aplicar, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void novaVersaoEncerraAAtivaECopiaOConteudo() throws Exception {
        int arroz = alimentoOficial("Arroz", 1);
        UUID paciente = paciente(ana);
        UUID anterior = dieta(ana, paciente);
        UUID almoco = refeicao(ana, anterior, "Almoço", "[1, 3, 5]");
        UUID item = item(ana, anterior, almoco, arroz, "120", null);
        adicionarSubstituto(ana, anterior, almoco, item, arroz, "90").andExpect(status().isCreated());
        patchComo(ana, "/v1/dietas/" + anterior + "/status", "{\"status\": \"ATIVA\"}").andExpect(status().isOk());

        UUID nova = uuid(postComo(ana, "/v1/dietas/" + anterior + "/nova-versao", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(anterior.toString())))
                .andExpect(jsonPath("$.dietaOrigemId").value(anterior.toString()))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.titulo").value("Plano " + sufixo))
                .andExpect(jsonPath("$.kcalMeta").value(2000.0))
                .andExpect(jsonPath("$.refeicoes.length()").value(1))
                .andExpect(jsonPath("$.refeicoes[0].diasSemana.length()").value(3))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].quantidadeG").value(120.0))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].substitutos.length()").value(1)));

        getComo(ana, "/v1/dietas/" + anterior).andExpect(jsonPath("$.status").value("ENCERRADA"));
        assertThat(contar("dieta WHERE paciente_id = ? AND status = 'ATIVA'", paciente)).isEqualTo(1);
        getComo(ana, "/v1/dietas?pacienteId=" + paciente + "&status=ENCERRADA")
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(anterior.toString()));

        // A nova versão é editável; a anterior, encerrada, continua congelada.
        postComo(ana, "/v1/dietas/" + nova + "/refeicoes", "{\"nome\": \"Ceia\"}").andExpect(status().isCreated());
        postComo(ana, "/v1/dietas/" + anterior + "/refeicoes", "{\"nome\": \"Ceia\"}").andExpect(status().isUnprocessableEntity());
        getComo(ana, "/v1/dietas/" + anterior).andExpect(jsonPath("$.refeicoes.length()").value(1));

        // Versão de um rascunho nasce rascunho e não encerra nada.
        UUID rascunho = dieta(ana, paciente);
        postComo(ana, "/v1/dietas/" + rascunho + "/nova-versao", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RASCUNHO"));
        getComo(ana, "/v1/dietas/" + rascunho).andExpect(jsonPath("$.status").value("RASCUNHO"));

        postComo(ana, "/v1/dietas/" + modelo(ana, "Modelo") + "/nova-versao", "").andExpect(status().isUnprocessableEntity());
        postComo(bruno, "/v1/dietas/" + nova + "/nova-versao", "").andExpect(status().isNotFound());
    }

    private int contar(String deOndeE, UUID parametro) {
        return jdbc.queryForObject("SELECT count(*) FROM " + deOndeE, Integer.class, parametro);
    }
}
