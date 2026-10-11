package br.com.afitcenter.dieta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class DietaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DietaApplication.class, args);
    }
}
