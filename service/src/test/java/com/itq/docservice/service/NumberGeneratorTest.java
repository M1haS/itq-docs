package com.itq.docservice.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NumberGeneratorTest {

    private final NumberGenerator generator = new NumberGenerator();

    @Test
    void generate_startsWithDocPrefix() {
        String number = generator.generate();
        assertThat(number).startsWith("DOC-");
    }

    @Test
    void generate_containsTodaysDate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String number = generator.generate();
        assertThat(number).contains(today);
    }

    @Test
    void generate_producesUniqueValues() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            numbers.add(generator.generate());
        }
        assertThat(numbers).hasSize(1000);
    }

    @Test
    void generate_matchesExpectedFormat() {
        String number = generator.generate();
        // Expected: DOC-yyyyMMdd-<number>
        assertThat(number).matches("DOC-\\d{8}-\\d+");
    }
}
