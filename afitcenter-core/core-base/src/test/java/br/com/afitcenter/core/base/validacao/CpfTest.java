package br.com.afitcenter.core.base.validacao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

    @Test
    void aceitaCpfValidoComOuSemPontuacao() {
        assertThat(Cpf.valido("529.982.247-25")).isTrue();
        assertThat(Cpf.valido("52998224725")).isTrue();
    }

    @Test
    void recusaDigitosVerificadoresErrados() {
        assertThat(Cpf.valido("52998224726")).isFalse();
        assertThat(Cpf.valido("12345678900")).isFalse();
    }

    @Test
    void recusaSequenciasRepetidasETamanhoErrado() {
        assertThat(Cpf.valido("11111111111")).isFalse();
        assertThat(Cpf.valido("5299822472")).isFalse();
        assertThat(Cpf.valido(null)).isFalse();
    }

    @Test
    void normalizaRemovendoPontuacao() {
        assertThat(Cpf.normalizar("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Cpf.normalizar("  ")).isNull();
    }
}
