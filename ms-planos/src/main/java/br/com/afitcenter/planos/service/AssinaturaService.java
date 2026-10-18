package br.com.afitcenter.planos.service;

import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.evento.AssinaturaEvento;
import br.com.afitcenter.core.mensageria.evento.TipoAlteracao;
import br.com.afitcenter.core.mensageria.outbox.Outbox;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.AssinaturaInternoResponse;
import br.com.afitcenter.planos.model.dto.Dtos.AssinaturaResponse;
import br.com.afitcenter.planos.model.entity.Assinatura;
import br.com.afitcenter.planos.model.entity.Plano;
import br.com.afitcenter.planos.model.enums.Enums.StatusAssinatura;
import br.com.afitcenter.planos.repository.AssinaturaRepository;
import br.com.afitcenter.planos.repository.AssinaturaRepository.Filtros;
import br.com.afitcenter.planos.repository.PlanoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assinatura de cada nutricionista e os eventos {@code assinatura.*} com os limites efetivos, consumidos pelo
 * ms-paciente e pelo ms-nutricionista. Toda alteração passa por {@link #salvarEPublicar}.
 */
@Service
@RequiredArgsConstructor
public class AssinaturaService {

    private static final int TAMANHO_MAXIMO_PAGINA = 200;

    private final AssinaturaRepository repository;
    private final PlanoRepository planoRepository;
    private final Outbox outbox;

    /** Assinatura do nutricionista; na primeira vez, criada no plano padrão. */
    @Transactional
    public Assinatura obterOuCriar(UUID nutricionistaId) {
        Optional<Assinatura> existente = repository.findByNutricionistaId(nutricionistaId);
        if (existente.isPresent()) return existente.get();

        boolean criada = repository.inserirSeNaoExistir(UUID.randomUUID(), nutricionistaId, padrao().getId()) > 0;
        Assinatura assinatura = repository.findByNutricionistaId(nutricionistaId).orElseThrow();
        if (criada) publicar(assinatura, TipoAlteracao.CRIADO);
        return assinatura;
    }

    @Transactional
    public AssinaturaResponse minha(UUID nutricionistaId) {
        return resposta(obterOuCriar(nutricionistaId));
    }

    /** Rota interna do core-assinatura: limites efetivos, criando a assinatura se preciso. */
    @Transactional
    public AssinaturaInternoResponse interno(UUID nutricionistaId) {
        Assinatura assinatura = obterOuCriar(nutricionistaId);
        return AssinaturaInternoResponse.de(assinatura, efetivo(assinatura));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AssinaturaResponse> listar(StatusAssinatura status, Integer planoId, int pagina, int tamanho) {
        var filtro = Specification.where(Filtros.comStatus(status)).and(Filtros.doPlano(planoId));
        var page = repository.findAll(filtro, PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA), Sort.by(Sort.Direction.DESC, "atualizadoEm")));
        return PaginaResponse.de(page, this::resposta);
    }

    /** Visão do administrador; o registro de auditoria fica ligado ao nutricionista consultado. */
    @Transactional
    public AssinaturaResponse doNutricionista(UUID nutricionistaId) {
        AuditoriaRequisicao.nutricionista(nutricionistaId);
        return minha(nutricionistaId);
    }

    public Plano padrao() {
        return planoRepository.findByPadraoTrue()
                .orElseThrow(() -> new IllegalStateException("Nenhum plano padrão configurado."));
    }

    /** Plano cujos limites valem agora: o contratado ou, com a assinatura suspensa, o padrão. */
    public Plano efetivo(Assinatura assinatura) {
        return assinatura.getStatus() == StatusAssinatura.SUSPENSA ? padrao() : assinatura.getPlano();
    }

    public AssinaturaResponse resposta(Assinatura assinatura) {
        return AssinaturaResponse.de(assinatura, efetivo(assinatura));
    }

    /** Grava e registra o evento na outbox, na transação corrente. */
    @Transactional
    public Assinatura salvarEPublicar(Assinatura assinatura) {
        Assinatura salva = repository.saveAndFlush(assinatura);
        publicar(salva, TipoAlteracao.ATUALIZADO);
        return salva;
    }

    /** Os limites de um plano mudaram: cada assinatura afetada ganha uma versão nova e um evento. */
    @Transactional
    public int republicarLimites(Plano plano) {
        List<UUID> ids = plano.isPadrao() ? repository.usandoLimitesDoPadrao(plano.getId())
                : repository.usandoLimitesDe(plano.getId());
        if (ids.isEmpty()) return 0;
        repository.incrementarVersao(ids);
        repository.findAllById(ids).forEach(a -> publicar(a, TipoAlteracao.ATUALIZADO));
        return ids.size();
    }

    private void publicar(Assinatura a, TipoAlteracao tipo) {
        Plano efetivo = efetivo(a);
        String routingKey = tipo == TipoAlteracao.CRIADO ? Eventos.ASSINATURA_CRIADA : Eventos.ASSINATURA_ATUALIZADA;
        outbox.registrar(routingKey, a.getId(), new AssinaturaEvento(UUID.randomUUID(), tipo, Instant.now(), a.getId(),
                a.getNutricionistaId(), a.getPlano().getCodigo(), a.getStatus().name(), efetivo.getLimitePacientes(),
                efetivo.getLimiteConsultorios(), a.getVersao()));
    }
}
