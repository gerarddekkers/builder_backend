package com.mentesme.builder.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(name = "builder.metro-prod.enabled", havingValue = "true")
public class MetroProdDataSourceConfig {

    @Bean
    @ConfigurationProperties("builder.metro-prod.datasource")
    public DataSourceProperties metroProdDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "metroProdDataSource")
    public DataSource metroProdDataSource(
            @Qualifier("metroProdDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "metroProdJdbcTemplate")
    public JdbcTemplate metroProdJdbcTemplate(
            @Qualifier("metroProdDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "metroProdTransactionManager")
    public PlatformTransactionManager metroProdTransactionManager(
            @Qualifier("metroProdDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
