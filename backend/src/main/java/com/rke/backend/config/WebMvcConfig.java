package com.rke.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.rke.backend.security.TenantScopeInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantScopeInterceptor tenantScopeInterceptor;

    public WebMvcConfig(TenantScopeInterceptor tenantScopeInterceptor) {
        this.tenantScopeInterceptor = tenantScopeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantScopeInterceptor)
                .addPathPatterns("/api/**")
                // Auth and health don't need tenant context.
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/health",
                        "/api/tenants/current",
                        "/actuator/**");
    }
}
