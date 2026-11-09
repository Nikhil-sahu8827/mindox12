package com.substring.docmind.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

@Slf4j
@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        String dbUrl = env.getProperty("SPRING_DATASOURCE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = env.getProperty("DATABASE_URL");
        }

        String username = env.getProperty("spring.datasource.username");
        String password = env.getProperty("spring.datasource.password");

        HikariConfig config = new HikariConfig();
        config.setPoolName("DocMindHikariCP");

        if (dbUrl != null && (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://"))) {
            log.info("Detected standard PostgreSQL URI in DATABASE_URL. Converting to JDBC format...");
            try {
                String raw = dbUrl.replace("jdbc:", "");
                URI uri = new URI(raw);
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                String query = uri.getQuery();

                if (uri.getUserInfo() != null) {
                    String[] userInfo = uri.getUserInfo().split(":", 2);
                    if (username == null || username.isBlank()) {
                        username = userInfo[0];
                    }
                    if (userInfo.length > 1 && (password == null || password.isBlank())) {
                        password = userInfo[1];
                    }
                }

                StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                        .append(host)
                        .append(":")
                        .append(port)
                        .append(path != null && !path.isBlank() ? path : "/neondb");

                if (query != null && !query.isBlank()) {
                    jdbcUrl.append("?").append(query);
                } else {
                    jdbcUrl.append("?sslmode=require");
                }

                config.setJdbcUrl(jdbcUrl.toString());
            } catch (Exception e) {
                log.warn("Failed parsing postgres URI ({}), using default: {}", e.getMessage(), dbUrl);
                config.setJdbcUrl(dbUrl.startsWith("jdbc:") ? dbUrl : "jdbc:" + dbUrl);
            }
        } else if (dbUrl != null && !dbUrl.isBlank()) {
            config.setJdbcUrl(dbUrl.startsWith("jdbc:") ? dbUrl : "jdbc:" + dbUrl);
        } else {
            String defaultUrl = env.getProperty("spring.datasource.url");
            config.setJdbcUrl(defaultUrl);
        }

        if (username != null && !username.isBlank()) {
            config.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }

        config.setDriverClassName("org.postgresql.Driver");

        int maxPool = 10;
        int minIdle = 2;
        try {
            String maxPoolStr = env.getProperty("spring.datasource.hikari.maximum-pool-size");
            if (maxPoolStr != null) maxPool = Integer.parseInt(maxPoolStr);
            String minIdleStr = env.getProperty("spring.datasource.hikari.minimum-idle");
            if (minIdleStr != null) minIdle = Integer.parseInt(minIdleStr);
        } catch (Exception ignored) {}

        config.setMaximumPoolSize(maxPool);
        config.setMinimumIdle(minIdle);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(20000);

        String maskedUrl = config.getJdbcUrl() != null ? config.getJdbcUrl().replaceAll(":[^:@]+@", ":***@") : "undefined";
        log.info("Initialized production DataSource with URL: {}", maskedUrl);

        return new HikariDataSource(config);
    }
}

