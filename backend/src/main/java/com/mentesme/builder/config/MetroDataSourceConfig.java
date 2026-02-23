package com.mentesme.builder.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@ConditionalOnProperty(name = "builder.metro.enabled", havingValue = "true")
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.mentesme.builder.repository",
    entityManagerFactoryRef = "metroEntityManagerFactory",
    transactionManagerRef = "metroTransactionManager"
)
public class MetroDataSourceConfig {

    @Bean
    @ConfigurationProperties("builder.metro.datasource")
    public DataSourceProperties metroDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "metroDataSource")
    public DataSource metroDataSource(@Qualifier("metroDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "metroJdbcTemplate")
    public JdbcTemplate metroJdbcTemplate(@Qualifier("metroDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "metroEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean metroEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("metroDataSource") DataSource dataSource) {
        return builder
            .dataSource(dataSource)
            .packages("com.mentesme.builder.entity")
            .persistenceUnit("metro")
            .build();
    }

    // JPA transaction manager - used by Spring Data JPA repositories
    @Bean(name = "metroTransactionManager")
    public PlatformTransactionManager metroTransactionManager(
            @Qualifier("metroEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    // JDBC transaction manager - used by publish service for raw SQL operations
    @Bean(name = "metroJdbcTransactionManager")
    public PlatformTransactionManager metroJdbcTransactionManager(
            @Qualifier("metroDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
