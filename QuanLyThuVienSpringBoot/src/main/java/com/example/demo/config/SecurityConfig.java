package com.example.demo.config;

import com.example.demo.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            CustomUserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            AuthenticationProvider authProvider,
            CustomUserDetailsService uds
    ) throws Exception {

        http.authenticationProvider(authProvider);
        http.csrf(csrf -> csrf.ignoringRequestMatchers(
                new AntPathRequestMatcher("/payment/ipn")
        ));

        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/", "/home", "/error",
                "/signin", "/signup", "/verify/**",
                "/forgot-password", "/reset-password",
                "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico",
                "/uploads/**", "/thumbnails/**", "/qr.png"
            ).permitAll()
            .requestMatchers("/books", "/books/**", "/category/**", "/search").permitAll()
            .requestMatchers("/membership/vnpay-return", "/membership/return").permitAll()
            .requestMatchers("/payment/ipn").permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        );
        http.formLogin(form -> form
            .loginPage("/signin").permitAll()
            .loginProcessingUrl("/signin")
            .usernameParameter("username")
            .passwordParameter("password")
            .defaultSuccessUrl("/home", true)
            .failureHandler((req, res, ex) -> {
                if (ex instanceof org.springframework.security.authentication.DisabledException) {
                    res.sendRedirect("/signin?error=unverified");
                } else if (ex instanceof org.springframework.security.authentication.LockedException) {
                    res.sendRedirect("/signin?error=locked");
                } else {
                    res.sendRedirect("/signin?error");
                }
            })
        );
        http.logout(logout -> logout
            .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
            .clearAuthentication(true)
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID", "remember-me")
            .logoutSuccessUrl("/signin?logout=true")
            .permitAll()
        );
        http.rememberMe(r -> r
            .key("eaut-secret-key") 
            .rememberMeParameter("remember-me")
            .tokenValiditySeconds(60 * 60 * 24 * 14)
            .userDetailsService(uds)
        );
        return http.build();
    }
}
