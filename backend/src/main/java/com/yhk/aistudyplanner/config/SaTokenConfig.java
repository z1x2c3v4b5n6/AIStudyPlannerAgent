package com.yhk.aistudyplanner.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    if (!"OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                        StpUtil.checkLogin();
                    }
                }))
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/register", "/api/v1/auth/login");
    }
}
