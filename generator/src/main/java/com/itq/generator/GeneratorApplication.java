package com.itq.generator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@SpringBootApplication
public class GeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeneratorApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate(
            @Value("${generator.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${generator.read-timeout-ms:10000}") int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    @Bean
    public CommandLineRunner run(
            RestTemplate restTemplate,
            @Value("${generator.service-url}") String serviceUrl,
            @Value("${generator.count}") int count,
            @Value("${generator.initiator}") String initiator) {
        return args -> {
            log.info("=== Document Generator started: N={}, target={} ===", count, serviceUrl);

            String url = serviceUrl + "/api/documents";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            AtomicInteger created = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            long overallStart = System.currentTimeMillis();

            for (int i = 1; i <= count; i++) {
                String title = "Generated Document #" + i;
                Map<String, String> body = Map.of("author", initiator, "title", title);

                long stepStart = System.currentTimeMillis();
                try {
                    ResponseEntity<Map> response = restTemplate.postForEntity(
                            url,
                            new HttpEntity<>(body, headers),
                            Map.class);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        int c = created.incrementAndGet();
                        log.info("Created [{}/{}] id={} in {}ms",
                                c, count, response.getBody().get("id"),
                                System.currentTimeMillis() - stepStart);
                    } else {
                        failed.incrementAndGet();
                        log.warn("Failed [{}/{}]: status={}", i, count, response.getStatusCode());
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Error creating document {}/{}: {}", i, count, e.getMessage());
                }

                if (i % 10 == 0) {
                    log.info("Progress: {}/{} created, {} failed",
                            created.get(), count, failed.get());
                }
            }

            long elapsed = System.currentTimeMillis() - overallStart;
            log.info("=== Generator finished: created={}/{}, failed={}, total time={}ms ===",
                    created.get(), count, failed.get(), elapsed);

            System.exit(0);
        };
    }
}
