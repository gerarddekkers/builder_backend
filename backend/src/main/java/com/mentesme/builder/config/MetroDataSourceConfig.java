package com.mentesme.builder.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "builder.metro.enabled", havingValue = "true")
public class MetroDataSourceConfig {

    @Bean
    @ConfigurationProperties("builder.metro.datasource")
    public DataSourceProperties metroDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "metroDataSource")
    public DataSource metroDataSource(@Qualifier("metroDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "metroJdbcTemplate")
    public JdbcTemplate metroJdbcTemplate(@Qualifier("metroDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
