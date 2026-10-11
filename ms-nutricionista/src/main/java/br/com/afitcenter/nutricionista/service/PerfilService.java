package br.com.afitcenter.nutricionista.service;

import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.nutricionista.model.dto.Dtos.AtualizarPerfilRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.NutricionistaResponse;
import br.com.afitcenter.nutricionista.model.entity.Nutricionista;
import br.com.afitcenter.nutricionista.repository.NutricionistaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final NutricionistaRepository repository;

    @Transactional(readOnly = true)
    public NutricionistaResponse buscar(UUID nutricionistaId) {
        return NutricionistaResponse.de(entidade(nutricionistaId));
    }

    /** CPF, CRN e e-mail não mudam por aqui (o e-mail é o login, gerido pelo ms-autenticacao). */
    @Transactional
    public NutricionistaResponse atualizar(UUID nutricionistaId, AtualizarPerfilRequest dados) {
        Nutricionista nutricionista = entidade(nutricionistaId);
        nutricionista.atualizarPerfil(dados.nome().trim(), dados.telefone(), dados.especialidades(),
                dados.assinaturaUrl(), dados.logoUrl(),
                dados.endereco() == null ? null : dados.endereco().paraEntidade());
        return NutricionistaResponse.de(nutricionista);
    }

    Nutricionista entidade(UUID nutricionistaId) {
        return repository.findById(nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de nutricionista não encontrado."));
    }
}
