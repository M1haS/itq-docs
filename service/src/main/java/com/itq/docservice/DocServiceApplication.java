package com.itq.docservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocServiceApplication.class, args);
    }
}
