package br.com.afitcenter.consulta;

import br.com.afitcenter.consulta.model.dto.Dtos.ConsultaRequest;
import br.com.afitcenter.consulta.model.enums.Enums.Modalidade;
import br.com.afitcenter.consulta.model.enums.Enums.TipoConsulta;
import br.com.afitcenter.consulta.replica.ConsultorioInternoClient;
import br.com.afitcenter.consulta.replica.ConsultorioRefService;
import br.com.afitcenter.consulta.service.ConsultaService;
import br.com.afitcenter.core.mensageria.evento.ConsultorioEvento;
import br.com.afitcenter.core.mensageria.evento.PacienteEvento;
import br.com.afitcenter.core.mensageria.evento.TipoAlteracao;
import br.com.afitcenter.core.paciente.PacienteEventosListener;
import br.com.afitcenter.core.paciente.PacienteInternoClient;
import br.com.afitcenter.core.paciente.PacienteRef;
import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import br.com.afitcenter.core.web.erro.ConflitoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteIntegracao
class ConsultaIntegracaoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PacienteEventosListener pacienteListener;
    @Autowired
    ConsultorioRefService consultorioRefService;
    @Autowired
    ConsultaService consultaService;
    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    PacienteInternoClient pacienteClient;
    @MockitoBean
    ConsultorioInternoClient consultorioClient;

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();

    @Test
    void agendaComNomesDasReplicasEFiltroPorPeriodo() throws Exception {
        UUID paciente = pacienteReplicado(ana, "Carlos", "ATIVO", 0);
        UUID consultorio = consultorioReplicado(ana, "Sala Centro", 0);

        agendar(ana, consulta(paciente, consultorio, "2026-11-03T09:00:00-03:00", 60))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacienteNome").value("Carlos"))
                .andExpect(jsonPath("$.consultorioNome").value("Sala Centro"))
                .andExpect(jsonPath("$.status").value("AGENDADA"));
        agendar(ana, consulta(paciente, null, "2026-11-10T09:00:00-03:00", 60)).andExpect(status().isCreated());

        mvc.perform(get("/v1/consultas").header("Authorization", TokensTeste.nutricionista(ana))
                        .param("inicio", "2026-11-01T00:00:00-03:00").param("fim", "2026-11-08T00:00:00-03:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].pacienteNome").value("Carlos"));
        mvc.perform(get("/v1/consultas").header("Authorization", TokensTeste.nutricionista(bruno)))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void pacienteAindaSemEventoEBuscadoNoMsPacienteEReplicado() throws Exception {
        UUID paciente = UUID.randomUUID();
        when(pacienteClient.buscar(paciente)).thenReturn(
                new PacienteRef(paciente, ana, "Recém-cadastrado", "F", LocalDate.of(1990, 1, 1), "ATIVO", false, 0));

        agendar(ana, consulta(paciente, null, "2026-11-04T10:00:00-03:00", 30))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacienteNome").value("Recém-cadastrado"));
        assertThat(jdbc.queryForObject("SELECT nome FROM paciente_ref WHERE id = ?", String.class, paciente))
                .isEqualTo("Recém-cadastrado");
    }

    @Test
    void pacienteOuConsultorioDeOutroNutricionistaRespondem404() throws Exception {
        UUID pacienteDoBruno = pacienteReplicado(bruno, "Do Bruno", "ATIVO", 0);
        UUID pacienteDaAna = pacienteReplicado(ana, "Da Ana", "ATIVO", 0);
        UUID consultorioDoBruno = consultorioReplicado(bruno, "Sala do Bruno", 0);

        agendar(ana, consulta(pacienteDoBruno, null, "2026-11-05T10:00:00-03:00", 60))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Paciente não encontrado."));
        agendar(ana, consulta(pacienteDaAna, consultorioDoBruno, "2026-11-05T10:00:00-03:00", 60))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Consultório não encontrado."));
    }

    @Test
    void conflitoDeHorarioECancelamentoLiberandoOHorario() throws Exception {
        UUID paciente = pacienteReplicado(ana, "Agenda cheia", "ATIVO", 0);
        UUID primeira = idDe(agendar(ana, consulta(paciente, null, "2026-11-06T09:00:00-03:00", 60))
                .andExpect(status().isCreated()));

        agendar(ana, consulta(paciente, null, "2026-11-06T09:30:00-03:00", 30))
                .andExpect(status().isConflict());
        // Encostar no fim da anterior não é conflito.
        agendar(ana, consulta(paciente, null, "2026-11-06T10:00:00-03:00", 30))
                .andExpect(status().isCreated());
        // A agenda é por nutricionista.
        agendar(bruno, consulta(pacienteReplicado(bruno, "Outro", "ATIVO", 0), null, "2026-11-06T09:00:00-03:00", 60))
                .andExpect(status().isCreated());

        mudarStatus(primeira, "CANCELADA").andExpect(status().isOk());
        agendar(ana, consulta(paciente, null, "2026-11-06T09:00:00-03:00", 60)).andExpect(status().isCreated());
    }

    @Test
    void marcacoesSimultaneasNoMesmoHorarioNaoPassamJuntas() throws Exception {
        UUID paciente = pacienteReplicado(ana, "Concorrência", "ATIVO", 0);
        var pedido = new ConsultaRequest(paciente, null, OffsetDateTime.parse("2026-11-12T09:00:00-03:00"), 60,
                TipoConsulta.RETORNO, Modalidade.ONLINE, null, null);
        var primeiraMarcou = new CountDownLatch(1);
        var liberarPrimeira = new CountDownLatch(1);
        var transacao = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // A primeira marcação grava e segura a transação aberta.
            Future<?> primeira = executor.submit(() -> transacao.executeWithoutResult(s -> {
                consultaService.criar(ana, pedido);
                primeiraMarcou.countDown();
                aguardar(liberarPrimeira);
            }));
            assertThat(primeiraMarcou.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> segunda = executor.submit(() -> consultaService.criar(ana, pedido));
            // Sem o bloqueio da agenda, a segunda não enxergaria a primeira (ainda não confirmada) e marcaria junto.
            assertThatThrownBy(() -> segunda.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

            liberarPrimeira.countDown();
            primeira.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> segunda.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ConflitoException.class);
        } finally {
            liberarPrimeira.countDown();
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consulta WHERE nutricionista_id = ?", Long.class, ana))
                .isEqualTo(1);
    }

    @Test
    void transicoesDeStatusEReagendamento() throws Exception {
        UUID paciente = pacienteReplicado(ana, "Status", "ATIVO", 0);
        UUID id = idDe(agendar(ana, consulta(paciente, null, "2026-11-07T09:00:00-03:00", 60)));

        mudarStatus(id, "CONFIRMADA").andExpect(status().isOk());
        mudarStatus(id, "REALIZADA").andExpect(jsonPath("$.status").value("REALIZADA"));
        mudarStatus(id, "CANCELADA")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Não é possível mudar a consulta de REALIZADA para CANCELADA."));

        mvc.perform(put("/v1/consultas/" + id).header("Authorization", TokensTeste.nutricionista(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(consulta(paciente, null, "2026-11-08T09:00:00-03:00", 60))))
                .andExpect(status().isUnprocessableEntity());

        ObjectNode soObservacao = consulta(paciente, null, "2026-11-07T09:00:00-03:00", 60).put("observacao", "Trouxe exames");
        mvc.perform(put("/v1/consultas/" + id).header("Authorization", TokensTeste.nutricionista(ana))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(soObservacao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observacao").value("Trouxe exames"));
    }

    @Test
    void pacienteInativoNaoPodeSerAgendado() throws Exception {
        UUID paciente = pacienteReplicado(ana, "Inativo", "INATIVO", 0);
        agendar(ana, consulta(paciente, null, "2026-11-09T09:00:00-03:00", 60))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void eventosAntigosNaoSobrescrevemAReplica() {
        UUID consultorio = consultorioReplicado(ana, "Sala Nova", 2);
        consultorioRefService.receber(new ConsultorioEvento(UUID.randomUUID(), TipoAlteracao.ATUALIZADO, Instant.now(),
                consultorio, ana, "Sala Velha", true, 1));

        UUID paciente = pacienteReplicado(ana, "Nome novo", "ATIVO", 5);
        pacienteReplicado(paciente, ana, "Nome antigo", "ATIVO", 4);

        assertThat(jdbc.queryForObject("SELECT nome FROM consultorio_ref WHERE id = ?", String.class, consultorio))
                .isEqualTo("Sala Nova");
        assertThat(jdbc.queryForObject("SELECT nome FROM paciente_ref WHERE id = ?", String.class, paciente))
                .isEqualTo("Nome novo");
    }

    @Test
    void analisesDaConsultaComIsolamento() throws Exception {
        UUID paciente = pacienteReplicado(ana, "Com análise", "ATIVO", 0);
        UUID consulta = idDe(agendar(ana, consulta(paciente, null, "2026-11-11T09:00:00-03:00", 60)));
        String url = "/v1/consultas/" + consulta + "/analises";

        String criada = mvc.perform(post(url).header("Authorization", TokensTeste.nutricionista(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"DIAGNOSTICO_NUTRICIONAL\",\"descricao\":\"Obesidade grau II\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID analise = UUID.fromString(json.readTree(criada).get("id").asText());

        mvc.perform(get(url).header("Authorization", TokensTeste.nutricionista(bruno))).andExpect(status().isNotFound());
        mvc.perform(post(url).header("Authorization", TokensTeste.nutricionista(bruno))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoria\":\"OUTRO\",\"descricao\":\"x\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete(url + "/" + analise).header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isNoContent());
        mvc.perform(get(url).header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT deletado_em IS NOT NULL FROM analise WHERE id = ?", Boolean.class, analise))
                .as("prontuário: exclusão lógica").isTrue();

        mvc.perform(get("/internal/consultas/" + consulta).header("X-Internal-Token", ChavesTeste.TOKEN_INTERNO))
                .andExpect(jsonPath("$.nutricionistaId").value(ana.toString()))
                .andExpect(jsonPath("$.pacienteId").value(paciente.toString()));
    }

    // ------------------------------------------------------------------ apoio

    private UUID pacienteReplicado(UUID nutricionista, String nome, String status, long versao) {
        return pacienteReplicado(UUID.randomUUID(), nutricionista, nome, status, versao);
    }

    private UUID pacienteReplicado(UUID id, UUID nutricionista, String nome, String status, long versao) {
        pacienteListener.receber(new PacienteEvento(UUID.randomUUID(), TipoAlteracao.CRIADO, Instant.now(), id,
                nutricionista, nome, "M", LocalDate.of(1995, 3, 10), status, false, versao));
        return id;
    }

    private UUID consultorioReplicado(UUID nutricionista, String nome, long versao) {
        UUID id = UUID.randomUUID();
        consultorioRefService.receber(new ConsultorioEvento(UUID.randomUUID(), TipoAlteracao.CRIADO, Instant.now(),
                id, nutricionista, nome, true, versao));
        return id;
    }

    private ObjectNode consulta(UUID paciente, UUID consultorio, String dataHora, int duracao) {
        ObjectNode corpo = json.createObjectNode()
                .put("pacienteId", paciente.toString())
                .put("dataHora", dataHora)
                .put("duracaoMin", duracao)
                .put("tipo", "RETORNO")
                .put("modalidade", consultorio == null ? "ONLINE" : "PRESENCIAL")
                .put("valor", 250.00);
        if (consultorio != null) corpo.put("consultorioId", consultorio.toString());
        return corpo;
    }

    private ResultActions agendar(UUID nutricionista, ObjectNode corpo) throws Exception {
        return mvc.perform(post("/v1/consultas").header("Authorization", TokensTeste.nutricionista(nutricionista))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)));
    }

    private ResultActions mudarStatus(UUID consulta, String status) throws Exception {
        return mvc.perform(patch("/v1/consultas/" + consulta + "/status").header("Authorization", TokensTeste.nutricionista(ana))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("O teste não liberou a transação.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private UUID idDe(ResultActions resultado) throws Exception {
        return UUID.fromString(json.readTree(resultado.andReturn().getResponse().getContentAsString()).get("id").asText());
    }
}
