package com.xiaoying.materialstudio.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiWebConfig implements WebMvcConfigurer {
    private final OriginGuardInterceptor originGuard;

    public ApiWebConfig(OriginGuardInterceptor originGuard) {
        this.originGuard = originGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(originGuard).addPathPatterns("/api/**");
    }
}
