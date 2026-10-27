package com.arquitechthor.kopi.bootstrap;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/// The [LocalAccessFilter], in front of everything (ADR-016).
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalAccessConfiguration.LocalAccessProperties.class)
class LocalAccessConfiguration {

    /// `kopi.local-access.*` in `application.yml`.
    ///
    /// @param allowedHosts the names this machine is reached by
    @ConfigurationProperties("kopi.local-access")
    record LocalAccessProperties(@DefaultValue({"localhost", "127.0.0.1", "[::1]"}) Set<String> allowedHosts) {}

    @Bean
    FilterRegistrationBean<LocalAccessFilter> localAccessFilter(LocalAccessProperties properties) {
        var registration = new FilterRegistrationBean<>(new LocalAccessFilter(properties.allowedHosts()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
