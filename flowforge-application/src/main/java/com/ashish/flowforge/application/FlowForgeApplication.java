package com.ashish.flowforge.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.ashish.flowforge")
public class FlowForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowForgeApplication.class, args);
    }
}
