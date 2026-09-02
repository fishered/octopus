package com.accuenergy.octopus.mgmt.infrastructure.config;

import com.accuenergy.octopus.common.persistence.OctopusTenantLineHandler;
import com.accuenergy.octopus.common.persistence.TenantDataSourceTransactionManager;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class PersistenceConfiguration {
    private static final Set<String> GLOBAL_TABLES = Set.of(
            "tenant", "account", "mfa_factor", "mfa_recovery_code", "auth_session", "permission", "menu", "unit_catalog",
            "security_audit_event", "role", "role_permission", "device_type", "thing_model", "parameter_definition",
            "thing_model_parameter",
            "account_login_security", "account_platform_role", "integration_outbox", "kafka_dlt_replay_job");

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new OctopusTenantLineHandler(GLOBAL_TABLES)));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean("tenantTransactionManager")
    @Primary
    PlatformTransactionManager tenantTransactionManager(DataSource dataSource) {
        return new TenantDataSourceTransactionManager(dataSource);
    }

    /** Only authentication/bootstrap and explicitly audited platform repositories may use this manager. */
    @Bean("platformTransactionManager")
    PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
