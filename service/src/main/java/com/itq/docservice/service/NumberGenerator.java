package com.itq.docservice.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class NumberGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong counter = new AtomicLong(System.currentTimeMillis());

    public String generate() {
        return "DOC-" + LocalDate.now().format(FMT) + "-" + counter.incrementAndGet();
    }
}
