package br.com.afitcenter.nutricionista;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class NutricionistaApplication {

    public static void main(String[] args) {
        SpringApplication.run(NutricionistaApplication.class, args);
    }
}
