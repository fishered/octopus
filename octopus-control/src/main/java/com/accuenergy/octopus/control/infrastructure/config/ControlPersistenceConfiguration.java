package com.accuenergy.octopus.control.infrastructure.config;

import com.accuenergy.octopus.common.persistence.OctopusTenantLineHandler;
import com.accuenergy.octopus.common.persistence.TenantDataSourceTransactionManager;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ControlPersistenceConfiguration {
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
                new OctopusTenantLineHandler(Set.of("command_status_outbox"))));
        return interceptor;
    }

    @Bean("tenantTransactionManager")
    @Primary
    PlatformTransactionManager tenantTransactionManager(DataSource dataSource) {
        return new TenantDataSourceTransactionManager(dataSource);
    }

    @Bean("platformTransactionManager")
    PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
