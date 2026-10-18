package br.com.afitcenter.auditoria;

import br.com.afitcenter.auditoria.service.RegistroService;
import br.com.afitcenter.auditoria.service.RetencaoAuditoria;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.mensageria.evento.AuditoriaEvento;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteIntegracao
@TestPropertySource(properties = {"afitcenter.auditoria.agendador.habilitado=false", "afitcenter.auditoria.retencao-dias=30"})
class AuditoriaIntegracaoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RegistroService service;

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final UUID paciente = UUID.randomUUID();

    @Test
    void gravaCadaEventoUmaVezSo() {
        AuditoriaEvento evento = evento(ana, ana, "LEITURA", "pacientes", paciente, Instant.now());
        service.receber(evento);
        service.receber(evento);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM registro_auditoria WHERE id = ?", Long.class,
                evento.eventoId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detalhes->>'id' FROM registro_auditoria WHERE id = ?", String.class,
                evento.eventoId())).isEqualTo(paciente.toString());
        assertThatThrownBy(() -> service.receber(new AuditoriaEvento(null, Instant.now(), "ms-x", "LEITURA", null, null,
                null, null, null, null, null, null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nutricionistaVeSoOProprioTenantEQuemAcessouCadaPaciente() throws Exception {
        UUID admin = UUID.randomUUID();
        service.receber(evento(ana, ana, "LEITURA", "pacientes", paciente, Instant.now().minusSeconds(30)));
        service.receber(evento(ana, ana, "ALTERACAO", "consultas", UUID.randomUUID(), Instant.now().minusSeconds(20)));
        service.receber(evento(ana, admin, "LEITURA", "pacientes", paciente, Instant.now().minusSeconds(10)));
        service.receber(evento(bruno, bruno, "LEITURA", "pacientes", UUID.randomUUID(), Instant.now()));

        mvc.perform(get("/v1/auditoria/registros").header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.conteudo[0].usuarioId").value(admin.toString()))
                .andExpect(jsonPath("$.conteudo[0].ip").doesNotExist())
                .andExpect(jsonPath("$.conteudo[1].ip").value("10.0.0.1"));
        mvc.perform(get("/v1/auditoria/registros").header("Authorization", TokensTeste.nutricionista(ana))
                        .param("pacienteId", paciente.toString()).param("acao", "LEITURA"))
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(get("/v1/auditoria/registros").header("Authorization", TokensTeste.nutricionista(bruno))
                        .param("pacienteId", paciente.toString()))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void administradorVeTodosOsTenantsECadaConsultaFicaRegistrada() throws Exception {
        UUID admin = UUID.randomUUID();
        service.receber(evento(ana, ana, "CRIACAO", "dietas", UUID.randomUUID(), Instant.now()));
        service.receber(evento(bruno, bruno, "CRIACAO", "dietas", UUID.randomUUID(), Instant.now()));

        mvc.perform(get("/v1/admin/auditoria/registros").header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/auditoria/registros").param("nutricionistaId", bruno.toString())
                        .header("Authorization", TokensTeste.comPapeis(admin, Papel.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].ip").value("10.0.0.1"));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM registro_auditoria
                WHERE usuario_id = ? AND recurso = 'auditoria' AND nutricionista_id = ?""", Long.class, admin, bruno))
                .as("a consulta do administrador entra na trilha do tenant consultado").isEqualTo(1);
        mvc.perform(get("/v1/auditoria/registros").header("Authorization", TokensTeste.nutricionista(bruno))
                        .param("recurso", "auditoria"))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void registrosNaoPodemSerAlteradosNemApagadosForaDaRetencao(@Autowired RetencaoAuditoria retencao) {
        AuditoriaEvento antigo = evento(ana, ana, "LEITURA", "pacientes", paciente, Instant.now().minus(Duration.ofDays(31)));
        AuditoriaEvento recente = evento(ana, ana, "LEITURA", "pacientes", paciente, Instant.now().minus(Duration.ofDays(29)));
        service.receber(antigo);
        service.receber(recente);

        assertThatThrownBy(() -> jdbc.update("UPDATE registro_auditoria SET acao = 'X' WHERE id = ?", antigo.eventoId()))
                .hasMessageContaining("não podem ser alterados");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM registro_auditoria WHERE id = ?", antigo.eventoId()))
                .hasMessageContaining("não podem ser alterados");

        assertThat(retencao.apagarAntigos(Instant.now())).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registro_auditoria WHERE id IN (?, ?)", Long.class,
                antigo.eventoId(), recente.eventoId())).isEqualTo(1);
    }

    @Test
    void periodoInvalidoResponde422() throws Exception {
        mvc.perform(get("/v1/auditoria/registros").header("Authorization", TokensTeste.nutricionista(ana))
                        .param("inicio", "2026-10-10T00:00:00-03:00").param("fim", "2026-10-01T00:00:00-03:00"))
                .andExpect(status().isUnprocessableEntity());
    }

    private static AuditoriaEvento evento(UUID tenant, UUID usuario, String acao, String recurso, UUID recursoId,
                                          Instant quando) {
        return new AuditoriaEvento(UUID.randomUUID(), quando, "ms-paciente", acao, recurso, recursoId.toString(),
                "pacientes".equals(recurso) ? recursoId : null, tenant, usuario, "NUTRICIONISTA", "GET",
                "/v1/" + recurso + "/{id}", 200, "10.0.0.1", Map.of("id", recursoId.toString()));
    }
}
