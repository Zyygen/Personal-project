package com.example.demo.config;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;


@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserRepository userRepository;

    public WebMvcConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/signin").setViewName("signin");
        registry.addViewController("/signup").setViewName("signup");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Chuẩn hoá path để tránh lỗi backslash trên Windows
        String base = uploadDir.replace("\\", "/");
        if (!base.endsWith("/")) base += "/";

        // /uploads/** -> file:[uploadDir]/**
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + base);

        registry.addResourceHandler("/static/**", "/assets/**", "/webjars/**")
                .addResourceLocations(
                        "classpath:/static/",
                        "classpath:/public/",
                        "classpath:/META-INF/resources/webjars/"
                );
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Đơn giản hoá: loại trừ luôn static paths khỏi interceptor
        registry.addInterceptor(new AccountStatusInterceptor(userRepository))
                .excludePathPatterns(
                        "/css/**","/js/**","/img/**",
                        "/static/**","/assets/**","/webjars/**",
                        "/uploads/**",
                        "/signin","/signup","/logout",
                        "/account/locked","/payment/**"
                )
                .addPathPatterns("/**");
    }

    static class AccountStatusInterceptor implements HandlerInterceptor {
        private final UserRepository userRepository;
        AccountStatusInterceptor(UserRepository userRepository) { this.userRepository = userRepository; }

        @Override
        public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return true;

            String uri = req.getRequestURI();
            String username = auth.getName();
            User user = userRepository.findByUsername(username);
            if (user == null) return true;

            var st = user.getStatus(); // ACTIVE / SUSPENDED / BANNED
            if (st == User.AccountStatus.SUSPENDED || st == User.AccountStatus.BANNED) {
                if (!(uri.startsWith("/payment") || uri.startsWith("/logout") || uri.startsWith("/account/locked"))) {
                    res.sendRedirect("/account/locked");
                    return false;
                }
            }
            return true;
        }
    }
}
