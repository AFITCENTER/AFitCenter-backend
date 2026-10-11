package br.com.afitcenter.paciente.service;

import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.paciente.model.dto.Dtos.MedicamentoRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.MedicamentoResponse;
import br.com.afitcenter.paciente.model.entity.PacienteMedicamento;
import br.com.afitcenter.paciente.repository.PacienteMedicamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Medicamentos, suplementos e fitoterápicos em uso pelo paciente. */
@Service
@RequiredArgsConstructor
public class MedicamentoService {

    private final PacienteMedicamentoRepository repository;
    private final PacienteService pacienteService;

    @Transactional(readOnly = true)
    public List<MedicamentoResponse> listar(UUID nutricionistaId, UUID pacienteId) {
        pacienteService.entidade(nutricionistaId, pacienteId);
        return repository.findAllByPacienteIdOrderByNome(pacienteId).stream().map(MedicamentoResponse::de).toList();
    }

    @Transactional
    public MedicamentoResponse adicionar(UUID nutricionistaId, UUID pacienteId, MedicamentoRequest dados) {
        pacienteService.entidade(nutricionistaId, pacienteId);
        PacienteMedicamento medicamento = new PacienteMedicamento(pacienteId);
        preencher(medicamento, dados);
        return MedicamentoResponse.de(repository.save(medicamento));
    }

    @Transactional
    public MedicamentoResponse atualizar(UUID nutricionistaId, UUID pacienteId, UUID id, MedicamentoRequest dados) {
        PacienteMedicamento medicamento = entidade(nutricionistaId, pacienteId, id);
        preencher(medicamento, dados);
        return MedicamentoResponse.de(medicamento);
    }

    @Transactional
    public void remover(UUID nutricionistaId, UUID pacienteId, UUID id) {
        repository.delete(entidade(nutricionistaId, pacienteId, id));
    }

    private PacienteMedicamento entidade(UUID nutricionistaId, UUID pacienteId, UUID id) {
        pacienteService.entidade(nutricionistaId, pacienteId);
        return repository.findByIdAndPacienteId(id, pacienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicamento não encontrado."));
    }

    private static void preencher(PacienteMedicamento m, MedicamentoRequest d) {
        m.preencher(d.tipo(), d.nome().trim(), d.dosagem(), d.frequencia(), d.inicio(), d.fim(), d.observacao());
    }
}
