package com.substring.docmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {
        org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration.class
})
@EnableJpaAuditing
@EnableAsync
public class DocmindBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocmindBackendApplication.class, args);
    }
}
