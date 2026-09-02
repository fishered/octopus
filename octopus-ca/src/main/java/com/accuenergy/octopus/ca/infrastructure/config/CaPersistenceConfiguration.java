package com.accuenergy.octopus.ca.infrastructure.config;

import com.accuenergy.octopus.ca.infrastructure.persistence.PlatformScopeDataSourceTransactionManager;
import com.accuenergy.octopus.common.persistence.TenantDataSourceTransactionManager;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CaPersistenceConfiguration {
    @Bean("tenantTransactionManager")
    @Primary
    PlatformTransactionManager tenantTransactionManager(DataSource dataSource) {
        return new TenantDataSourceTransactionManager(dataSource);
    }

    @Bean("platformTransactionManager")
    PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
        return new PlatformScopeDataSourceTransactionManager(dataSource);
    }
}
