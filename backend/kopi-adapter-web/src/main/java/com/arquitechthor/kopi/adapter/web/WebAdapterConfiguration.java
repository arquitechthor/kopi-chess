package com.arquitechthor.kopi.adapter.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/// Spring configuration of the web adapter, imported explicitly by
/// `kopi-bootstrap`.
///
/// Component scanning is limited to this package: adapters may use Spring
/// freely, but the core rings are wired by hand (ADR-002).
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = WebAdapterConfiguration.class)
public class WebAdapterConfiguration implements WebMvcConfigurer {

    /// Query parameter that switches the language: `?lang=es`, `?lang=en`.
    static final String LANGUAGE_PARAMETER = "lang";

    @Bean
    LocaleResolver localeResolver() {
        return new KopiLocaleResolver();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var languageSwitch = new LocaleChangeInterceptor();
        languageSwitch.setParamName(LANGUAGE_PARAMETER);
        languageSwitch.setIgnoreInvalidLocale(true);
        registry.addInterceptor(languageSwitch);
    }
}
