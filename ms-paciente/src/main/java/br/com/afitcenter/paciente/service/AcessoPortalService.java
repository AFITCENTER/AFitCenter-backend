package br.com.afitcenter.paciente.service;

import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.seguranca.LimiteTentativas;
import br.com.afitcenter.paciente.model.dto.Dtos.CodigoAcessoResponse;
import br.com.afitcenter.paciente.model.dto.Dtos.FichaPortalResponse;
import br.com.afitcenter.paciente.model.entity.CodigoAcesso;
import br.com.afitcenter.paciente.model.entity.Paciente;
import br.com.afitcenter.paciente.repository.CodigoAcessoRepository;
import br.com.afitcenter.paciente.repository.PacienteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso do paciente ao portal. O nutricionista gera um código para a ficha e o entrega ao paciente; o paciente,
 * logado com a própria conta (ms-pessoa), informa o código e a ficha passa a aparecer no portal.
 *
 * <p>Nenhuma ficha é ligada só por CPF ou e-mail: quem decide quem vê a ficha é o nutricionista, ao entregar o
 * código. O código é de uso único, vence em {@code afitcenter.portal.codigo-validade-horas} e os erros contam num
 * limite por conta, para ninguém testar códigos em sequência.</p>
 */
@Service
public class AcessoPortalService {

    /** Sem 0/O e 1/I, para o código ser ditado sem confusão. 32 símbolos × 8 posições = 40 bits. */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_CODIGO = 8;

    private final PacienteRepository pacientes;
    private final CodigoAcessoRepository codigos;
    private final PacienteService pacienteService;
    private final LimiteTentativas tentativasCodigo;
    private final Duration validade;
    private final SecureRandom aleatorio = new SecureRandom();

    public AcessoPortalService(PacienteRepository pacientes, CodigoAcessoRepository codigos,
                               PacienteService pacienteService, LimiteTentativas tentativasCodigo,
                               @Value("${afitcenter.portal.codigo-validade-horas:72}") long validadeHoras) {
        this.pacientes = pacientes;
        this.codigos = codigos;
        this.pacienteService = pacienteService;
        this.tentativasCodigo = tentativasCodigo;
        this.validade = Duration.ofHours(validadeHoras);
    }

    /** Gera (ou substitui) o código da ficha. A ficha não pode estar ligada a uma conta. */
    @Transactional
    public CodigoAcessoResponse gerarCodigo(UUID nutricionistaId, UUID pacienteId) {
        Paciente ficha = pacienteService.entidade(nutricionistaId, pacienteId);
        if (ficha.getPessoaId() != null)
            throw new ConflitoException("Este paciente já tem acesso ao portal. Desfaça o acesso atual para gerar outro código.");

        String codigo = sortear();
        Instant expiraEm = Instant.now().plus(validade);
        CodigoAcesso registro = codigos.findById(pacienteId).orElseGet(() -> new CodigoAcesso(pacienteId));
        registro.renovar(hash(codigo), expiraEm);
        codigos.save(registro);
        return new CodigoAcessoResponse(codigo.substring(0, 4) + "-" + codigo.substring(4), expiraEm);
    }

    /** Desliga a conta da ficha e invalida o código pendente. Idempotente. */
    @Transactional
    public void revogar(UUID nutricionistaId, UUID pacienteId) {
        Paciente ficha = pacienteService.entidade(nutricionistaId, pacienteId);
        codigos.findById(pacienteId).ifPresent(codigos::delete);
        if (ficha.getPessoaId() == null) return;
        ficha.desvincularPessoa();
        pacientes.saveAndFlush(ficha);
    }

    /** O paciente logado informa o código e a ficha passa a ser dele no portal. */
    @Transactional
    public FichaPortalResponse vincular(UUID pessoaId, String codigo) {
        String chave = pessoaId.toString();
        tentativasCodigo.verificarBloqueio(chave);

        Instant agora = Instant.now();
        Optional<Paciente> encontrada = Optional.ofNullable(normalizar(codigo))
                .flatMap(normalizado -> codigos.findByCodigoHash(hash(normalizado)))
                .filter(registro -> !registro.expirado(agora))
                .flatMap(registro -> pacientes.findByIdAndDeletadoEmIsNull(registro.getPacienteId()));
        if (encontrada.isEmpty()) {
            tentativasCodigo.registrarFalha(chave);
            throw new RegraNegocioException("Código de acesso inválido ou vencido. Peça um novo ao seu nutricionista.");
        }
        tentativasCodigo.registrarSucesso(chave);

        Paciente ficha = encontrada.get();
        // A trilha do nutricionista mostra que o paciente passou a acessar a ficha.
        AuditoriaRequisicao.nutricionista(ficha.getNutricionistaId());
        AuditoriaRequisicao.paciente(ficha.getId());
        AuditoriaRequisicao.recursoId(ficha.getId());
        if (pacientes.existsByNutricionistaIdAndPessoaIdAndDeletadoEmIsNull(ficha.getNutricionistaId(), pessoaId))
            throw new ConflitoException("Sua conta já está ligada a uma ficha deste nutricionista.");
        if (ficha.getPessoaId() != null)
            throw new ConflitoException("Esta ficha já está ligada a outra conta.");

        ficha.vincularPessoa(pessoaId);
        codigos.deleteById(ficha.getId());
        pacientes.saveAndFlush(ficha);
        return FichaPortalResponse.de(ficha);
    }

    /** Fichas ligadas à conta, de todos os nutricionistas. */
    @Transactional(readOnly = true)
    public List<FichaPortalResponse> fichas(UUID pessoaId) {
        return pacientes.findAllByPessoaIdAndDeletadoEmIsNullOrderByPessoaVinculadaEm(pessoaId).stream()
                .map(FichaPortalResponse::de).toList();
    }

    /** Aceita minúsculas, espaços e hífen ({@code abcd-efgh}); {@code null} se não tiver o tamanho certo. */
    static String normalizar(String codigo) {
        if (codigo == null) return null;
        String limpo = codigo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return limpo.length() == TAMANHO_CODIGO ? limpo : null;
    }

    static String hash(String codigoNormalizado) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(codigoNormalizado.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }

    private String sortear() {
        StringBuilder codigo = new StringBuilder(TAMANHO_CODIGO);
        for (int i = 0; i < TAMANHO_CODIGO; i++)
            codigo.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        return codigo.toString();
    }
}
