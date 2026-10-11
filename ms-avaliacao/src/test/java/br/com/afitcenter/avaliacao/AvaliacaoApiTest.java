package br.com.afitcenter.avaliacao;

import br.com.afitcenter.avaliacao.model.dto.Dtos.ConsultaInterna;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD de avaliações, regras de métrica e região, isolamento e vínculo com a consulta. */
class AvaliacaoApiTest extends IntegracaoBase {

    @Test
    void crudComImcCalculadoNoBancoEResultadosSubstituidos() throws Exception {
        UUID paciente = paciente(ana);
        ObjectNode gordura = resultado("GORDURA_PCT", null, "36.6")
                .put("refMin", 10).put("refMax", 20).put("classificacao", "Alto");
        ObjectNode corpo = avaliacao(paciente, "2026-10-08T07:26:00-03:00", "108.4",
                resultado("PERIMETRO_CM", "CINTURA", "118.0"),
                gordura,
                resultado("GORDURA_SEG_KG", "TRONCO", "21.9"),
                resultado("impedancia_20khz", "braco_dir", "263.4"));

        UUID id = idDe(enviar(ana, post("/v1/avaliacoes"), corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imc").value(36.22))
                .andExpect(jsonPath("$.pacienteNome").value("Paciente"))
                .andExpect(jsonPath("$.resultados.length()").value(4))
                // ordem do catálogo, não a do envio
                .andExpect(jsonPath("$.resultados[0].metricaCodigo").value("GORDURA_PCT"))
                .andExpect(jsonPath("$.resultados[0].classificacao").value("Alto"))
                .andExpect(jsonPath("$.resultados[2].regiaoCodigo").value("BRACO_DIR")));
        UUID linhaGordura = idDoResultado(id, "GORDURA_PCT");

        ObjectNode alterada = avaliacao(paciente, "2026-10-08T07:26:00-03:00", "100.0",
                resultado("GORDURA_PCT", null, "35.0"),
                resultado("GORDURA_SEG_KG", "TRONCO", "21.0"),
                resultado("DOBRA_CUTANEA_MM", "DC_TRICIPITAL", "30.5"));
        enviar(ana, put("/v1/avaliacoes/" + id), alterada)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pesoKg").value(100.0))
                .andExpect(jsonPath("$.imc").value(33.41))
                .andExpect(jsonPath("$.resultados.length()").value(3))
                .andExpect(jsonPath("$.resultados[0].valor").value(35.0))
                .andExpect(jsonPath("$.resultados[0].classificacao").doesNotExist());
        assertThat(idDoResultado(id, "GORDURA_PCT")).as("mesma métrica e região: atualizada no lugar").isEqualTo(linhaGordura);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM avaliacao_resultado WHERE avaliacao_id = ?",
                Integer.class, id)).isEqualTo(3);

        chamar(ana, get("/v1/avaliacoes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[2].metricaCodigo").value("DOBRA_CUTANEA_MM"));

        enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-09-01T08:00:00-03:00", "110.2"))
                .andExpect(status().isCreated());
        chamar(ana, get("/v1/avaliacoes").param("pacienteId", paciente.toString()))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[0].id").value(id.toString()))
                .andExpect(jsonPath("$.conteudo[0].resultados").doesNotExist());
        chamar(ana, get("/v1/avaliacoes").param("pacienteId", paciente.toString())
                        .param("inicio", "2026-10-01T00:00:00-03:00").param("fim", "2026-11-01T00:00:00-03:00"))
                .andExpect(jsonPath("$.totalElementos").value(1));
        chamar(ana, get("/v1/avaliacoes").param("inicio", "2026-10-01T00:00:00-03:00")
                        .param("fim", "2026-09-01T00:00:00-03:00"))
                .andExpect(status().isUnprocessableEntity());

        ObjectNode outroPaciente = avaliacao(paciente(ana), "2026-10-08T07:26:00-03:00", "100.0");
        enviar(ana, put("/v1/avaliacoes/" + id), outroPaciente).andExpect(status().isUnprocessableEntity());

        chamar(ana, delete("/v1/avaliacoes/" + id)).andExpect(status().isNoContent());
        chamar(ana, get("/v1/avaliacoes/" + id)).andExpect(status().isNotFound());
        chamar(ana, get("/v1/avaliacoes").param("pacienteId", paciente.toString()))
                .andExpect(jsonPath("$.totalElementos").value(1));
        assertThat(jdbc.queryForObject("SELECT deletado_em IS NOT NULL FROM avaliacao WHERE id = ?", Boolean.class, id))
                .as("prontuário: exclusão lógica").isTrue();
    }

    @Test
    void outroNutricionistaNaoEnxergaNemAlteraAvaliacao() throws Exception {
        UUID paciente = paciente(ana);
        UUID id = idDe(enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "80.0"))
                .andExpect(status().isCreated()));

        chamar(bruno, get("/v1/avaliacoes/" + id)).andExpect(status().isNotFound());
        enviar(bruno, put("/v1/avaliacoes/" + id), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "81.0"))
                .andExpect(status().isNotFound());
        chamar(bruno, delete("/v1/avaliacoes/" + id)).andExpect(status().isNotFound());
        enviar(bruno, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "81.0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Paciente não encontrado."));
        chamar(bruno, get("/v1/avaliacoes").param("pacienteId", paciente.toString()))
                .andExpect(jsonPath("$.totalElementos").value(0));

        chamar(ana, get("/v1/avaliacoes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pesoKg").value(80.0));
    }

    @Test
    void regrasDeMetricaERegiao() throws Exception {
        UUID paciente = paciente(ana);

        rejeitada(paciente, "PESO não entra nos resultados", resultado("PESO", null, "80"));
        rejeitada(paciente, "GLICEMIA_JEJUM é uma métrica laboratorial", resultado("GLICEMIA_JEJUM", null, "92"));
        rejeitada(paciente, "GORDURA_SEG_KG exige a região corporal", resultado("GORDURA_SEG_KG", null, "21.9"));
        rejeitada(paciente, "IMPEDANCIA_50KHZ exige a região corporal", resultado("IMPEDANCIA_50KHZ", " ", "240"));
        rejeitada(paciente, "GORDURA_PCT é um valor do corpo inteiro e não aceita região",
                resultado("GORDURA_PCT", "TRONCO", "36.6"));
        rejeitada(paciente, "A região TRONCO não se aplica a PERIMETRO_CM", resultado("PERIMETRO_CM", "TRONCO", "118"));
        rejeitada(paciente, "A região CINTURA não se aplica a DOBRA_CUTANEA_MM",
                resultado("DOBRA_CUTANEA_MM", "CINTURA", "30"));
        rejeitada(paciente, "Resultado repetido: GORDURA_SEG_KG em TRONCO",
                resultado("GORDURA_SEG_KG", "TRONCO", "21.9"), resultado("gordura_seg_kg", "tronco", "22.0"));
        rejeitada(paciente, "Resultado repetido: GORDURA_PCT",
                resultado("GORDURA_PCT", null, "36.6"), resultado("GORDURA_PCT", "", "36.7"));
        rejeitada(paciente, "Métrica desconhecida: NAO_EXISTE", resultado("NAO_EXISTE", null, "1"));
        rejeitada(paciente, "Região corporal desconhecida: OMBRO", resultado("PERIMETRO_CM", "OMBRO", "40"));
        rejeitada(paciente, "Faixa de referência inválida em GORDURA_PCT",
                resultado("GORDURA_PCT", null, "36.6").put("refMin", 25).put("refMax", 10));

        // A mesma métrica em regiões diferentes é o normal num laudo segmentar.
        enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "108.4",
                        resultado("GORDURA_SEG_KG", "BRACO_DIR", "2.9"), resultado("GORDURA_SEG_KG", "BRACO_ESQ", "3.0")))
                .andExpect(status().isCreated());

        ObjectNode semPeso = avaliacao(paciente, "2026-10-08T07:26:00-03:00", "1");
        semPeso.remove("pesoKg");
        enviar(ana, post("/v1/avaliacoes"), semPeso)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.pesoKg").exists());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM avaliacao WHERE paciente_id = ?", Integer.class, paciente))
                .as("nada gravado pelas tentativas recusadas").isEqualTo(1);
    }

    @Test
    void consultaVinculadaPrecisaSerDoMesmoPacienteENutricionista() throws Exception {
        UUID paciente = paciente(ana);
        UUID consulta = consulta(paciente, ana);
        UUID deOutroPaciente = consulta(paciente(ana), ana);
        UUID deOutroNutricionista = consulta(paciente, bruno);
        UUID inexistente = UUID.randomUUID();
        when(consultaClient.buscar(inexistente)).thenThrow(new FeignException.NotFound("Not Found",
                Request.create(Request.HttpMethod.GET, "/internal/consultas/" + inexistente, Map.of(), null,
                        StandardCharsets.UTF_8, null), null, Map.of()));

        for (UUID invalida : List.of(deOutroPaciente, deOutroNutricionista, inexistente)) {
            enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "80.0")
                            .put("consultaId", invalida.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Consulta não encontrada."));
            enviar(ana, post("/v1/exames"), exame(paciente, "Hemograma", "2026-10-01")
                            .put("consultaId", invalida.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Consulta não encontrada."));
        }

        ObjectNode corpo = avaliacao(paciente, "2026-10-08T07:26:00-03:00", "80.0").put("consultaId", consulta.toString());
        UUID id = idDe(enviar(ana, post("/v1/avaliacoes"), corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consultaId").value(consulta.toString())));
        enviar(ana, put("/v1/avaliacoes/" + id), corpo.put("pesoKg", 79.5)).andExpect(status().isOk());
        verify(consultaClient, times(1)).buscar(consulta);

        enviar(ana, put("/v1/avaliacoes/" + id), corpo.put("consultaId", deOutroPaciente.toString()))
                .andExpect(status().isNotFound());
        enviar(ana, post("/v1/exames"), exame(paciente, "Hemograma", "2026-10-01").put("consultaId", consulta.toString()))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ apoio

    private void rejeitada(UUID paciente, String mensagem, ObjectNode... resultados) throws Exception {
        enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "108.4", resultados))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString(mensagem)));
    }

    private UUID consulta(UUID paciente, UUID nutricionista) {
        UUID id = UUID.randomUUID();
        when(consultaClient.buscar(id)).thenReturn(
                new ConsultaInterna(id, paciente, nutricionista, OffsetDateTime.now(), "REALIZADA"));
        return id;
    }

    private UUID idDoResultado(UUID avaliacao, String metrica) {
        return jdbc.queryForObject("""
                SELECT r.id FROM avaliacao_resultado r JOIN metrica m ON m.id = r.metrica_id
                WHERE r.avaliacao_id = ? AND m.codigo = ? AND r.regiao_id IS NULL""", UUID.class, avaliacao, metrica);
    }
}
