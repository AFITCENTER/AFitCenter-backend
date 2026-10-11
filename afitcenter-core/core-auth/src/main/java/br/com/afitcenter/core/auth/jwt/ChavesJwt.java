package br.com.afitcenter.core.auth.jwt;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Leitura das chaves ECDSA P-256 (ES256) do JWT.
 *
 * <p>Aceita PEM ou o DER em Base64 numa única linha, o formato mais prático para
 * variáveis de ambiente:</p>
 * <ul>
 *     <li>chave privada: PKCS#8 ({@code -----BEGIN PRIVATE KEY-----});</li>
 *     <li>chave pública: X.509 ({@code -----BEGIN PUBLIC KEY-----}).</li>
 * </ul>
 */
public final class ChavesJwt {

    private static final int TAMANHO_CURVA_ES256 = 256;

    private ChavesJwt() {
    }

    public static ECPublicKey chavePublica(String valor) {
        byte[] der = decodificar(valor, "pública");
        try {
            var chave = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
            return validarCurva((ECPublicKey) chave, "pública");
        } catch (GeneralSecurityException | ClassCastException exception) {
            throw new IllegalStateException("A chave pública do JWT não é uma chave EC X.509 válida.", exception);
        }
    }

    public static ECPrivateKey chavePrivada(String valor) {
        byte[] der = decodificar(valor, "privada");
        try {
            var chave = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
            return validarCurva((ECPrivateKey) chave, "privada");
        } catch (GeneralSecurityException | ClassCastException exception) {
            throw new IllegalStateException("A chave privada do JWT não é uma chave EC PKCS#8 válida.", exception);
        }
    }

    private static byte[] decodificar(String valor, String tipo) {
        if (valor == null || valor.isBlank())
            throw new IllegalStateException("A chave " + tipo + " do JWT precisa ser configurada.");
        String base64 = valor
                .replaceAll("-----(BEGIN|END) [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("A chave " + tipo + " do JWT não está em Base64/PEM.", exception);
        }
    }

    private static <T extends ECKey> T validarCurva(T chave, String tipo) {
        if (chave.getParams().getCurve().getField().getFieldSize() != TAMANHO_CURVA_ES256)
            throw new IllegalStateException("A chave " + tipo + " do JWT precisa usar a curva P-256 (ES256).");
        return chave;
    }
}
