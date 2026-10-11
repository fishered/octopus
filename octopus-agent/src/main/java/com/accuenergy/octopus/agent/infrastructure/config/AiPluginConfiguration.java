package com.accuenergy.octopus.agent.infrastructure.config;

import com.accuenergy.octopus.ai.spi.AiProviderPlugin;
import com.accuenergy.octopus.ai.spi.AiProviderPluginRegistry;
import com.accuenergy.octopus.ai.spi.AiProviderRouter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiPluginConfiguration {
    @Bean
    AiProviderPluginRegistry aiProviderPluginRegistry(List<AiProviderPlugin> providers) {
        return new AiProviderPluginRegistry(providers);
    }

    @Bean
    AiProviderRouter aiProviderRouter(AiProviderPluginRegistry registry,
            @Value("${octopus.ai.default-provider:disabled}") String defaultProvider) {
        return new AiProviderRouter(registry, defaultProvider);
    }
}
