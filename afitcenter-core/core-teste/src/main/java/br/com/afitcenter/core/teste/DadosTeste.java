package br.com.afitcenter.core.teste;

import br.com.afitcenter.core.base.validacao.Cpf;

import java.util.concurrent.ThreadLocalRandom;

/** Geradores de dados válidos e únicos para os testes. */
public final class DadosTeste {

    private DadosTeste() {
    }

    /** CPF com dígitos verificadores corretos e prefixo aleatório (não colide entre testes). */
    public static String cpf() {
        String nove = String.format("%09d", ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999));
        return Cpf.comDigitosVerificadores(nove);
    }

    public static String email(String prefixo) {
        return prefixo + "-" + Long.toHexString(ThreadLocalRandom.current().nextLong()) + "@teste.local";
    }
}
