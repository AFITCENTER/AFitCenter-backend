package br.com.afitcenter.consulta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class ConsultaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultaApplication.class, args);
    }
}
