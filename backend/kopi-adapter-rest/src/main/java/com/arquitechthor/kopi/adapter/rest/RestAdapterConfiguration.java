package com.arquitechthor.kopi.adapter.rest;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/// Spring configuration of the REST adapter, imported explicitly by
/// `kopi-bootstrap`.
///
/// Component scanning is limited to this package. The controllers need the
/// use cases (`CreateGameUseCase`, `PlayMoveUseCase`…), which bootstrap
/// registers as beans (ADR-002).
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = RestAdapterConfiguration.class)
public class RestAdapterConfiguration {

    /// Every endpoint lives under this prefix.
    static final String API = "/api/v1";
}
