package com.mentesme.builder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class AssessmentBuilderApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssessmentBuilderApplication.class, args);
    }
}
