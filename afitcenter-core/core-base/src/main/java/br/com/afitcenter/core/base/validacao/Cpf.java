package br.com.afitcenter.core.base.validacao;

/** Normalização e validação de CPF (dígitos verificadores). */
public final class Cpf {

    private Cpf() {
    }

    /** Remove pontuação; devolve {@code null} para valores vazios. */
    public static String normalizar(String cpf) {
        if (cpf == null) return null;
        String digitos = cpf.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    public static boolean valido(String cpf) {
        String digitos = normalizar(cpf);
        if (digitos == null || digitos.length() != 11) return false;
        if (digitos.chars().distinct().count() == 1) return false; // 000.000.000-00, 111...

        return digitoVerificador(digitos, 9) == digitos.charAt(9) - '0'
                && digitoVerificador(digitos, 10) == digitos.charAt(10) - '0';
    }

    /** Completa 9 dígitos com os 2 verificadores (útil para gerar CPFs de teste). */
    public static String comDigitosVerificadores(String noveDigitos) {
        if (noveDigitos == null || !noveDigitos.matches("\\d{9}"))
            throw new IllegalArgumentException("Informe exatamente 9 dígitos.");
        String dez = noveDigitos + digitoVerificador(noveDigitos, 9);
        return dez + digitoVerificador(dez, 10);
    }

    private static int digitoVerificador(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++)
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
