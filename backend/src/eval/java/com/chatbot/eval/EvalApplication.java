package com.chatbot.eval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.chatbot"})
public class EvalApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvalApplication.class);
        app.setAdditionalProfiles("eval");
        app.run(args);
    }
}
