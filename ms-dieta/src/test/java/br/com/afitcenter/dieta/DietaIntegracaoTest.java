package br.com.afitcenter.dieta;

import br.com.afitcenter.dieta.integracao.ConsultaInternoClient.ConsultaRef;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Catálogo, regras do esquema, CRUD de dietas e modelos, refeições, itens e substitutos. */
class DietaIntegracaoTest extends IntegracaoBase {

    @Test
    void alimentosDaBaseOficialMaisOsProprios() throws Exception {
        alimentoOficial("Arroz teste", 1);
        alimentoOficial("Feijao teste", 14);
        jdbc.update("""
                INSERT INTO alimento (nutricionista_id, grupo_alimentar_id, nome, fonte, kcal) VALUES (?, 13, ?, 'PROPRIO', 100)""",
                ana, "Marmita da Ana teste " + sufixo);

        getComo(ana, "/v1/alimentos?busca=teste " + sufixo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Arroz teste " + sufixo))
                .andExpect(jsonPath("$.conteudo[?(@.proprio == true)].grupo").value("Alimentos preparados"));
        getComo(bruno, "/v1/alimentos?busca=teste " + sufixo).andExpect(jsonPath("$.totalElementos").value(2));
        getComo(ana, "/v1/grupos-alimentares").andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void esquemaDaDietaGarantePacienteDoMesmoNutricionistaEModeloSemPaciente() {
        UUID paciente = paciente(ana);

        assertThatCode(() -> dietaNoBanco(ana, paciente, false)).doesNotThrowAnyException();
        assertThatCode(() -> dietaNoBanco(ana, null, true)).as("modelo reutilizável").doesNotThrowAnyException();
        assertThatThrownBy(() -> dietaNoBanco(bruno, paciente, false)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> dietaNoBanco(ana, paciente, true)).isInstanceOf(DataIntegrityViolationException.class);

        UUID dieta = dietaNoBanco(ana, paciente, false);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO refeicao (id, dieta_id, nome, ordem, dias_semana) VALUES (?, ?, 'X', 1, '{8}')",
                UUID.randomUUID(), dieta)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dietaDePacienteEModeloComIsolamentoEntreNutricionistas() throws Exception {
        UUID paciente = paciente(ana, "Carla", "ATIVO");

        UUID dieta = uuid(postComo(ana, "/v1/dietas", """
                {"pacienteId": "%s", "titulo": " Hipocalórica ", "kcalMeta": 1800.5, "dataInicio": "2026-11-01"}
                """.formatted(paciente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Hipocalórica"))
                .andExpect(jsonPath("$.pacienteNome").value("Carla"))
                .andExpect(jsonPath("$.modelo").value(false))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.kcalMeta").value(1800.5)));
        UUID modelo = uuid(postComo(ana, "/v1/dietas", "{\"modelo\": true, \"titulo\": \"Low carb\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modelo").value(true))
                .andExpect(jsonPath("$.pacienteId").isEmpty()));

        postComo(ana, "/v1/dietas", "{\"modelo\": true, \"pacienteId\": \"%s\", \"titulo\": \"X\"}".formatted(paciente))
                .andExpect(status().isUnprocessableEntity());
        postComo(ana, "/v1/dietas", "{\"titulo\": \"Sem paciente\"}").andExpect(status().isUnprocessableEntity());
        postComo(ana, "/v1/dietas", "{\"pacienteId\": \"%s\", \"titulo\": \" \"}".formatted(paciente))
                .andExpect(status().isBadRequest());
        postComo(ana, "/v1/dietas", """
                {"pacienteId": "%s", "titulo": "Datas", "dataInicio": "2026-11-10", "dataFim": "2026-11-01"}
                """.formatted(paciente)).andExpect(status().isUnprocessableEntity());
        postComo(bruno, "/v1/dietas", "{\"pacienteId\": \"%s\", \"titulo\": \"X\"}".formatted(paciente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Paciente não encontrado."));
        postComo(ana, "/v1/dietas", "{\"pacienteId\": \"%s\", \"titulo\": \"X\"}".formatted(paciente(ana, "Inativa", "INATIVO")))
                .andExpect(status().isUnprocessableEntity());

        getComo(ana, "/v1/dietas?pacienteId=" + paciente)
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(dieta.toString()));
        // Modelos ficam fora da listagem de dietas.
        getComo(ana, "/v1/dietas").andExpect(jsonPath("$.totalElementos").value(1));
        getComo(ana, "/v1/modelos").andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(modelo.toString()));
        getComo(ana, "/v1/modelos?busca=LOW").andExpect(jsonPath("$.totalElementos").value(1));
        getComo(bruno, "/v1/dietas?pacienteId=" + paciente).andExpect(jsonPath("$.totalElementos").value(0));
        getComo(bruno, "/v1/modelos").andExpect(jsonPath("$.totalElementos").value(0));

        for (UUID id : new UUID[]{dieta, modelo}) {
            String url = "/v1/dietas/" + id;
            getComo(bruno, url).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Dieta não encontrada."));
            putComo(bruno, url, "{\"modelo\": true, \"titulo\": \"X\"}").andExpect(status().isNotFound());
            deleteComo(bruno, url).andExpect(status().isNotFound());
            getComo(bruno, url + "/refeicoes").andExpect(status().isNotFound());
            postComo(bruno, url + "/refeicoes", "{\"nome\": \"Ceia\"}").andExpect(status().isNotFound());
            getComo(bruno, url + "/totais").andExpect(status().isNotFound());
            getComo(bruno, url + "/alertas").andExpect(status().isNotFound());
            postComo(bruno, url + "/nova-versao", "").andExpect(status().isNotFound());
        }

        putComo(ana, "/v1/dietas/" + dieta, "{\"pacienteId\": \"%s\", \"titulo\": \"Hipocalórica II\", \"objetivo\": \"Perder 5 kg\"}"
                .formatted(paciente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Hipocalórica II"))
                .andExpect(jsonPath("$.kcalMeta").isEmpty());
        putComo(ana, "/v1/dietas/" + dieta, "{\"pacienteId\": \"%s\", \"titulo\": \"Troca\"}".formatted(paciente(ana)))
                .andExpect(status().isUnprocessableEntity());
        putComo(ana, "/v1/dietas/" + modelo, "{\"modelo\": false, \"pacienteId\": \"%s\", \"titulo\": \"X\"}".formatted(paciente))
                .andExpect(status().isUnprocessableEntity());

        deleteComo(ana, "/v1/dietas/" + dieta).andExpect(status().isNoContent());
        getComo(ana, "/v1/dietas/" + dieta).andExpect(status().isNotFound());
        getComo(ana, "/v1/dietas?pacienteId=" + paciente).andExpect(jsonPath("$.totalElementos").value(0));
        assertThat(jdbc.queryForObject("SELECT deletado_em IS NOT NULL FROM dieta WHERE id = ?", Boolean.class, dieta))
                .as("exclusão lógica").isTrue();
    }

    @Test
    void consultaInformadaEValidadaNoMsConsulta() throws Exception {
        UUID paciente = paciente(ana);
        UUID consulta = consultaDe(ana, paciente);
        UUID consultaDoBruno = consultaDe(bruno, paciente(bruno));
        UUID inexistente = UUID.randomUUID();
        when(consultaClient.buscar(inexistente)).thenThrow(new FeignException.NotFound("nao encontrada",
                Request.create(Request.HttpMethod.GET, "/internal/consultas/x", Map.of(), null, StandardCharsets.UTF_8, null),
                null, Map.of()));

        UUID dieta = uuid(postComo(ana, "/v1/dietas", corpoComConsulta(paciente, consulta))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consultaId").value(consulta.toString())));

        postComo(ana, "/v1/dietas", corpoComConsulta(paciente(ana), consulta))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A consulta informada é de outro paciente."));
        postComo(ana, "/v1/dietas", corpoComConsulta(paciente, consultaDoBruno))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Consulta não encontrada."));
        postComo(ana, "/v1/dietas", corpoComConsulta(paciente, inexistente)).andExpect(status().isNotFound());
        postComo(ana, "/v1/dietas", "{\"modelo\": true, \"titulo\": \"M\", \"consultaId\": \"%s\"}".formatted(consulta))
                .andExpect(status().isUnprocessableEntity());
        putComo(ana, "/v1/dietas/" + dieta, corpoComConsulta(paciente, consultaDoBruno)).andExpect(status().isNotFound());
        putComo(ana, "/v1/dietas/" + dieta, corpoComConsulta(paciente, consulta)).andExpect(status().isOk());
    }

    @Test
    void umaDietaAtivaPorPacienteEEncerradaFicaCongelada() throws Exception {
        UUID paciente = paciente(ana);
        UUID primeira = dieta(ana, paciente);
        UUID segunda = dieta(ana, paciente);
        UUID refeicao = refeicao(ana, primeira, "Almoço", null);

        mudarStatus(primeira, "ATIVA").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATIVA"));
        mudarStatus(segunda, "ATIVA").andExpect(status().isConflict());
        mudarStatus(primeira, "RASCUNHO").andExpect(status().isUnprocessableEntity());
        mudarStatus(primeira, "ENCERRADA").andExpect(status().isOk());
        mudarStatus(segunda, "ATIVA").andExpect(status().isOk());
        mudarStatus(modelo(ana, "Modelo"), "ATIVA").andExpect(status().isUnprocessableEntity());

        String encerrada = "/v1/dietas/" + primeira;
        putComo(ana, encerrada, "{\"pacienteId\": \"%s\", \"titulo\": \"X\"}".formatted(paciente))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A dieta está encerrada e não pode ser alterada. Crie uma nova versão."));
        postComo(ana, encerrada + "/refeicoes", "{\"nome\": \"Ceia\"}").andExpect(status().isUnprocessableEntity());
        deleteComo(ana, encerrada + "/refeicoes/" + refeicao).andExpect(status().isUnprocessableEntity());
        adicionarItem(ana, primeira, refeicao, alimentoOficial("Maçã", 3), "100", null, 1)
                .andExpect(status().isUnprocessableEntity());
        getComo(ana, encerrada).andExpect(status().isOk()).andExpect(jsonPath("$.refeicoes.length()").value(1));
    }

    @Test
    void refeicoesComDiasDaSemanaEOrdem() throws Exception {
        UUID dieta = dieta(ana, paciente(ana));
        String refeicoes = "/v1/dietas/" + dieta + "/refeicoes";

        UUID cafe = uuid(postComo(ana, refeicoes, "{\"nome\": \"Café da manhã\", \"horario\": \"07:30\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horario").value("07:30:00"))
                .andExpect(jsonPath("$.ordem").value(1))
                .andExpect(jsonPath("$.diasSemana", contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.itens.length()").value(0)));
        UUID lanche = uuid(postComo(ana, refeicoes, "{\"nome\": \"Lanche\", \"diasSemana\": [6, 1, 6]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordem").value(2))
                .andExpect(jsonPath("$.diasSemana", contains(1, 6))));
        postComo(ana, refeicoes, "{\"nome\": \"X\", \"diasSemana\": [8]}").andExpect(status().isBadRequest());
        postComo(ana, refeicoes, "{\"nome\": \"X\", \"diasSemana\": [0]}").andExpect(status().isBadRequest());
        postComo(ana, refeicoes, "{\"nome\": \"X\", \"diasSemana\": []}").andExpect(status().isBadRequest());

        putComo(ana, refeicoes + "/" + lanche, "{\"nome\": \"Lanche da tarde\", \"horario\": \"16:00\", \"diasSemana\": [3, 2, 1]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Lanche da tarde"))
                .andExpect(jsonPath("$.ordem").value(2))
                .andExpect(jsonPath("$.diasSemana", contains(1, 2, 3)));
        assertThat(jdbc.queryForObject("SELECT array_to_string(dias_semana, ',') FROM refeicao WHERE id = ?", String.class, lanche))
                .isEqualTo("1,2,3");

        getComo(ana, refeicoes).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].nome").value("Café da manhã"));
        putComo(ana, "/v1/dietas/" + dieta(ana, paciente(ana)) + "/refeicoes/" + cafe, "{\"nome\": \"Outra dieta\"}")
                .andExpect(status().isNotFound());
        deleteComo(ana, refeicoes + "/" + cafe).andExpect(status().isNoContent());
        getComo(ana, refeicoes).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void itensComMedidaCaseiraESubstitutosSoComAlimentosVisiveis() throws Exception {
        int arroz = alimentoOficial("Arroz", 1, "100", "128", "2.5", "28.1", "0.2", "1.6");
        int colher = medidaOficial(arroz, "Colher de sopa cheia", "25");
        int feijao = alimentoOficial("Feijão", 14);
        int concha = medidaOficial(feijao, "Concha média", "80");
        int daAna = alimentoProprio(ana, "Farofa da Ana");
        int doBruno = alimentoProprio(bruno, "Farofa do Bruno");

        UUID dieta = dieta(ana, paciente(ana));
        UUID refeicao = refeicao(ana, dieta, "Almoço", null);

        UUID itemArroz = uuid(adicionarItem(ana, dieta, refeicao, arroz, "4", colher, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidadeG").value(100.0))
                .andExpect(jsonPath("$.medida").value("Colher de sopa cheia"))
                .andExpect(jsonPath("$.alimentoNome").value("Arroz " + sufixo))
                .andExpect(jsonPath("$.macros.kcal").value(128.0))
                .andExpect(jsonPath("$.ordem").value(1)));
        adicionarItem(ana, dieta, refeicao, feijao, "150", null, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidadeG").value(150.0))
                .andExpect(jsonPath("$.medida").isEmpty());
        adicionarItem(ana, dieta, refeicao, arroz, "2", concha, 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A medida caseira informada não é deste alimento."));
        adicionarItem(ana, dieta, refeicao, doBruno, "100", null, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Alimento não encontrado."));
        adicionarItem(ana, dieta, refeicao, daAna, "50", null, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.opcao").value(2));
        adicionarItem(ana, dieta, refeicao, arroz, "0", null, 1).andExpect(status().isBadRequest());
        adicionarItem(bruno, dieta, refeicao, arroz, "100", null, 1).andExpect(status().isNotFound());

        String substitutos = itens(dieta, refeicao) + "/" + itemArroz + "/substitutos";
        UUID substituto = uuid(adicionarSubstituto(ana, dieta, refeicao, itemArroz, daAna, "80")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidadeG").value(80.0))
                .andExpect(jsonPath("$.macros.kcal").value(160.0)));
        adicionarSubstituto(ana, dieta, refeicao, itemArroz, doBruno, "80").andExpect(status().isNotFound());
        putComo(ana, substitutos + "/" + substituto, "{\"alimentoId\": %d, \"quantidade\": 3, \"alimentoMedidaId\": %d}"
                .formatted(arroz, colher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeG").value(75.0));

        putComo(ana, itens(dieta, refeicao) + "/" + itemArroz,
                "{\"alimentoId\": %d, \"quantidade\": 3, \"alimentoMedidaId\": %d}".formatted(arroz, colher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeG").value(75.0))
                .andExpect(jsonPath("$.ordem").value(1))
                .andExpect(jsonPath("$.substitutos.length()").value(1));

        getComo(ana, "/v1/dietas/" + dieta)
                .andExpect(jsonPath("$.titulo").value("Plano " + sufixo))
                .andExpect(jsonPath("$.refeicoes[0].itens.length()").value(3))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].id").value(itemArroz.toString()))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].substitutos[0].id").value(substituto.toString()))
                .andExpect(jsonPath("$.refeicoes[0].itens[2].opcao").value(2));

        // O alimento próprio excluído continua nos itens que já o usam.
        deleteComo(ana, "/v1/alimentos/" + daAna).andExpect(status().isNoContent());
        getComo(ana, "/v1/dietas/" + dieta).andExpect(jsonPath("$.refeicoes[0].itens[2].alimentoNome")
                .value("Farofa da Ana " + sufixo));
        adicionarItem(ana, dieta, refeicao, daAna, "50", null, 1).andExpect(status().isNotFound());

        deleteComo(ana, substitutos + "/" + substituto).andExpect(status().isNoContent());
        adicionarSubstituto(ana, dieta, refeicao, itemArroz, feijao, "100").andExpect(status().isCreated());
        deleteComo(ana, itens(dieta, refeicao) + "/" + itemArroz).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refeicao_item_substituto WHERE refeicao_item_id = ?",
                Integer.class, itemArroz)).as("substitutos vão junto com o item").isZero();
        deleteComo(ana, itens(dieta, refeicao) + "/" + itemArroz).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ apoio

    private UUID dietaNoBanco(UUID nutricionista, UUID paciente, boolean modelo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dieta (id, nutricionista_id, paciente_id, is_modelo, titulo) VALUES (?, ?, ?, ?, 'Plano')""",
                id, nutricionista, paciente, modelo);
        return id;
    }

    private UUID consultaDe(UUID nutricionista, UUID paciente) {
        UUID id = UUID.randomUUID();
        when(consultaClient.buscar(id)).thenReturn(new ConsultaRef(id, paciente, nutricionista, OffsetDateTime.now(), "REALIZADA"));
        return id;
    }

    private static String corpoComConsulta(UUID paciente, UUID consulta) {
        return "{\"pacienteId\": \"%s\", \"consultaId\": \"%s\", \"titulo\": \"Pós-consulta\"}".formatted(paciente, consulta);
    }

    private ResultActions mudarStatus(UUID dieta, String status) throws Exception {
        return patchComo(ana, "/v1/dietas/" + dieta + "/status", "{\"status\": \"" + status + "\"}");
    }
}
