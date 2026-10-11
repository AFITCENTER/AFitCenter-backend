package br.com.afitcenter.avaliacao;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Equipamentos, metas e exames (com resultados e anexos), incluindo o isolamento entre nutricionistas. */
class CadastrosApiTest extends IntegracaoBase {

    @Test
    void equipamentosDoSistemaEProprios() throws Exception {
        chamar(ana, get("/v1/equipamentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.marca == 'Relaxmedic')].doSistema").value(true));

        int proprio = idInteiroDe(enviar(ana, post("/v1/equipamentos"), equipamento("ADIPOMETRO", "Cescorf", "Cientifico"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doSistema").value(false)));
        enviar(ana, post("/v1/equipamentos"), equipamento("ADIPOMETRO", " cescorf ", "CIENTIFICO"))
                .andExpect(status().isConflict());
        enviar(ana, post("/v1/equipamentos"),
                equipamento("BIOIMPEDANCIA", "Relaxmedic", "Balança de bioimpedância (modelo a confirmar)"))
                .andExpect(status().isConflict());

        // O mesmo cadastro é livre para outro nutricionista, que não enxerga o da Ana.
        int doBruno = idInteiroDe(enviar(bruno, post("/v1/equipamentos"), equipamento("ADIPOMETRO", "Cescorf", "Cientifico"))
                .andExpect(status().isCreated()));
        chamar(bruno, get("/v1/equipamentos"))
                .andExpect(jsonPath("$[?(@.id == " + proprio + ")]").isEmpty());
        enviar(bruno, put("/v1/equipamentos/" + proprio), equipamento("FITA", "X", "Y")).andExpect(status().isNotFound());
        chamar(bruno, delete("/v1/equipamentos/" + proprio)).andExpect(status().isNotFound());

        enviar(ana, put("/v1/equipamentos/" + proprio), equipamento("ADIPOMETRO", "Cescorf", "Innovare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("Innovare"));

        Integer doSistema = jdbc.queryForObject(
                "SELECT min(id) FROM equipamento WHERE nutricionista_id IS NULL", Integer.class);
        enviar(ana, put("/v1/equipamentos/" + doSistema), equipamento("FITA", "X", "Y"))
                .andExpect(status().isUnprocessableEntity());
        chamar(ana, delete("/v1/equipamentos/" + doSistema)).andExpect(status().isUnprocessableEntity());

        // Na avaliação: o do sistema e o próprio valem; o de outro nutricionista não existe.
        UUID paciente = paciente(ana);
        enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "80.0")
                        .put("equipamentoId", doBruno))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Equipamento não encontrado."));
        enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-01T07:26:00-03:00", "80.5")
                        .put("equipamentoId", doSistema))
                .andExpect(status().isCreated());
        enviar(ana, post("/v1/avaliacoes"), avaliacao(paciente, "2026-10-08T07:26:00-03:00", "80.0")
                        .put("equipamentoId", proprio))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipamentoId").value(proprio));

        chamar(ana, delete("/v1/equipamentos/" + proprio)).andExpect(status().isConflict());
        chamar(bruno, delete("/v1/equipamentos/" + doBruno)).andExpect(status().isNoContent());
        chamar(bruno, get("/v1/equipamentos"))
                .andExpect(jsonPath("$[?(@.id == " + doBruno + ")]").isEmpty());
    }

    @Test
    void metasDoPaciente() throws Exception {
        UUID paciente = paciente(ana);
        String url = "/v1/pacientes/" + paciente + "/metas";

        UUID meta = idDe(enviar(ana, post(url), meta("peso", "95", null, "2027-03-31"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metricaCodigo").value("PESO"))
                .andExpect(jsonPath("$.unidade").value("kg"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.dataInicio").value(LocalDate.now().toString())));
        enviar(ana, post(url), meta("GLICEMIA_JEJUM", "99", "2026-10-01", null)).andExpect(status().isCreated());

        enviar(ana, post(url), meta("GORDURA_SEG_KG", "10", null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("medida por região corporal")));
        enviar(ana, post(url), meta("GORDURA_PCT", "30", "2026-12-01", "2026-11-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("O prazo não pode ser anterior à data de início."));
        enviar(ana, post(url), meta("NAO_EXISTE", "1", null, null)).andExpect(status().isUnprocessableEntity());

        ObjectNode atingida = meta("PESO", "95", null, "2027-03-31").put("status", "ATINGIDA");
        enviar(ana, put(url + "/" + meta), atingida)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATINGIDA"))
                .andExpect(jsonPath("$.dataInicio").value(LocalDate.now().toString()));
        chamar(ana, get(url)).andExpect(jsonPath("$.length()").value(2));
        chamar(ana, get(url).param("status", "ATIVA"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].metricaCodigo").value("GLICEMIA_JEJUM"));
        chamar(ana, get(url + "/" + meta)).andExpect(status().isOk());

        chamar(bruno, get(url)).andExpect(status().isNotFound());
        chamar(bruno, get(url + "/" + meta)).andExpect(status().isNotFound());
        enviar(bruno, post(url), meta("PESO", "90", null, null)).andExpect(status().isNotFound());
        enviar(bruno, put(url + "/" + meta), atingida).andExpect(status().isNotFound());
        chamar(bruno, delete(url + "/" + meta)).andExpect(status().isNotFound());
        // A meta só existe sob o próprio paciente.
        chamar(ana, get("/v1/pacientes/" + paciente(ana) + "/metas/" + meta)).andExpect(status().isNotFound());

        chamar(ana, delete(url + "/" + meta)).andExpect(status().isNoContent());
        chamar(ana, get(url + "/" + meta)).andExpect(status().isNotFound());
    }

    @Test
    void examesComResultadosEAnexos() throws Exception {
        UUID paciente = paciente(ana);
        UUID exame = idDe(enviar(ana, post("/v1/exames"), exame(paciente, "Perfil lipídico", "2026-09-20",
                        resultadoExame("ldl", "130"), resultadoExame("HDL", "45.5").put("refMin", 40)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultados.length()").value(2))
                .andExpect(jsonPath("$.resultados[0].metricaCodigo").value("HDL"))
                .andExpect(jsonPath("$.resultados[0].unidade").value("mg/dL"))
                .andExpect(jsonPath("$.anexos.length()").value(0)));

        enviar(ana, post("/v1/exames"), exame(paciente, "Bioimpedância", "2026-09-20", resultadoExame("GORDURA_PCT", "30")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("GORDURA_PCT não é uma métrica laboratorial")));
        enviar(ana, post("/v1/exames"), exame(paciente, "Repetido", "2026-09-20",
                        resultadoExame("HDL", "45"), resultadoExame("hdl", "46")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Resultado repetido: HDL."));
        enviar(ana, post("/v1/exames"), exame(paciente, "Futuro", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isBadRequest());

        enviar(ana, put("/v1/exames/" + exame), exame(paciente, "Perfil lipídico", "2026-09-20",
                        resultadoExame("TRIGLICERIDEOS", "150.5"), resultadoExame("HDL", "50.5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados.length()").value(2))
                .andExpect(jsonPath("$.resultados[0].metricaCodigo").value("HDL"))
                .andExpect(jsonPath("$.resultados[0].valor").value(50.5))
                .andExpect(jsonPath("$.resultados[1].metricaCodigo").value("TRIGLICERIDEOS"));

        String anexos = "/v1/exames/" + exame + "/anexos";
        UUID anexo = idDe(enviar(ana, post(anexos), anexo("exames/" + exame + "/laudo.pdf", "application/pdf"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tamanhoBytes").value(182044)));
        enviar(ana, post(anexos), anexo("x", "pdf")).andExpect(status().isBadRequest());
        chamar(ana, get("/v1/exames/" + exame))
                .andExpect(jsonPath("$.anexos.length()").value(1))
                .andExpect(jsonPath("$.anexos[0].nomeArquivo").value("laudo.pdf"));

        chamar(bruno, get("/v1/exames/" + exame)).andExpect(status().isNotFound());
        enviar(bruno, put("/v1/exames/" + exame), exame(paciente, "Perfil lipídico", "2026-09-20"))
                .andExpect(status().isNotFound());
        enviar(bruno, post(anexos), anexo("y", "image/png")).andExpect(status().isNotFound());
        chamar(bruno, delete(anexos + "/" + anexo)).andExpect(status().isNotFound());
        enviar(bruno, post("/v1/exames"), exame(paciente, "Hemograma", "2026-09-20")).andExpect(status().isNotFound());

        chamar(ana, delete(anexos + "/" + anexo)).andExpect(status().isNoContent());
        chamar(ana, delete(anexos + "/" + anexo)).andExpect(status().isNotFound());

        enviar(ana, post("/v1/exames"), exame(paciente, "Glicemia", "2026-06-10", resultadoExame("GLICEMIA_JEJUM", "92")))
                .andExpect(status().isCreated());
        chamar(ana, get("/v1/exames").param("pacienteId", paciente.toString()))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Perfil lipídico"));
        chamar(ana, get("/v1/exames").param("pacienteId", paciente.toString())
                        .param("inicio", "2026-09-01").param("fim", "2026-09-20"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(exame.toString()));
        chamar(bruno, get("/v1/exames").param("pacienteId", paciente.toString()))
                .andExpect(jsonPath("$.totalElementos").value(0));

        chamar(bruno, delete("/v1/exames/" + exame)).andExpect(status().isNotFound());
        chamar(ana, delete("/v1/exames/" + exame)).andExpect(status().isNoContent());
        chamar(ana, get("/v1/exames/" + exame)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT deletado_em IS NOT NULL FROM exame WHERE id = ?", Boolean.class, exame))
                .as("prontuário: exclusão lógica").isTrue();
    }

    private ObjectNode equipamento(String tipo, String marca, String modelo) {
        return json.createObjectNode().put("tipo", tipo).put("marca", marca).put("modelo", modelo);
    }

    private ObjectNode anexo(String storageKey, String mimeType) {
        return json.createObjectNode().put("storageKey", storageKey).put("nomeArquivo", "laudo.pdf")
                .put("mimeType", mimeType).put("tamanhoBytes", 182044);
    }
}
