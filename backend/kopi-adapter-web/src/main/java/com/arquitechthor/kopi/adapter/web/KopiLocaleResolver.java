package com.arquitechthor.kopi.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

/// Remembers the visitor's language in a cookie.
///
/// - First visit: the language comes from the browser's `Accept-Language`.
/// - After choosing one with `?lang=`, the cookie wins.
/// - Only supported languages are ever stored (see [UiLanguage]).
final class KopiLocaleResolver extends CookieLocaleResolver {

    static final String COOKIE_NAME = "kopi-lang";

    KopiLocaleResolver() {
        super(COOKIE_NAME);
        setCookieMaxAge(Duration.ofDays(365));
        setDefaultLocaleFunction(request ->
                UiLanguage.fromAcceptLanguage(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)).locale());
    }

    @Override
    public void setLocaleContext(HttpServletRequest request, @Nullable HttpServletResponse response,
                                 @Nullable LocaleContext localeContext) {
        var requested = localeContext == null ? null : localeContext.getLocale();
        var supported = requested == null ? null : new SimpleLocaleContext(UiLanguage.closestTo(requested).locale());
        super.setLocaleContext(request, response, supported);
    }
}
