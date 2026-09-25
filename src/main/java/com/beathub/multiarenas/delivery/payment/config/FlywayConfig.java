package com.beathub.multiarenas.delivery.payment.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Value("${spring.flyway.enabled:true}")
    private boolean flywayEnabled;

    @Bean
    public static BeanFactoryPostProcessor entityManagerDependsOnFlywayPostProcessor() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                beanFactory.getBeanDefinition("entityManagerFactory").setDependsOn("flyway");
            }
        };
    }

    @Bean(name = "flyway")
    public Flyway flyway(DataSource dataSource) {
        if (!flywayEnabled) {
            return null;
        }
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("payment")
                .defaultSchema("payment")
                .createSchemas(true)
                .baselineOnMigrate(false)
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();
        return flyway;
    }
}
