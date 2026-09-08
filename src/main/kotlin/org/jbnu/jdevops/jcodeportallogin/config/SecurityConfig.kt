package org.jbnu.jdevops.jcodeportallogin.config

import org.jbnu.jdevops.jcodeportallogin.security.CustomAuthenticationSuccessHandler
import org.jbnu.jdevops.jcodeportallogin.security.CustomLogoutSuccessHandler
import org.jbnu.jdevops.jcodeportallogin.security.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.http.MediaType

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Value("\${front.domain}")
    private lateinit var frontDomain: String

    @Value("\${router.domain}")
    private lateinit var routerDomain: String

    @Bean
    fun securityFilterChain(http: HttpSecurity,
                            customAuthenticationSuccessHandler: CustomAuthenticationSuccessHandler,
                            customLogoutSuccessHandler: CustomLogoutSuccessHandler,
                            jwtAuthenticationFilter: JwtAuthenticationFilter
    ): SecurityFilterChain {
        http
            // CSRF 보호 활성화하되, API 경로는 CSRF 검증에서 제외 (필요에 따라 조정)
            .csrf { csrf ->
                csrf.ignoringRequestMatchers("/api/**", "/oidc/login", "/logout")
            }
            .cors { cors ->
                cors.configurationSource {
                    val configuration = CorsConfiguration()
                    configuration.allowedOrigins = listOf(frontDomain, routerDomain)
                    configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    configuration.allowedHeaders = listOf("*")
                    configuration.exposedHeaders = listOf("Authorization", "Set-Cookie")
                    configuration.allowCredentials = true
                    configuration
                }
            }
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers("/login", "/error", "/oauth2/**").permitAll()
                    .requestMatchers("/api/auth/signup", "/api/auth/login/basic", "/api/auth/login/oidc/success").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/refresh").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated()  // 모든 요청에 대해 인증 요구
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { _, response, _ ->
                    response.status = 401
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write("{\"error\":\"인증이 필요합니다.\"}")
                }
                exceptions.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write("{\"error\":\"접근 권한이 없습니다.\"}")
                }
            }
            .oauth2Login { oauth2 ->
                // 기본 성공 URL 대신 커스텀 성공 핸들러 사용
                oauth2.successHandler(customAuthenticationSuccessHandler)
            }
            .logout { logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/")
                    .logoutSuccessHandler(customLogoutSuccessHandler)
                    .invalidateHttpSession(true)
                    .deleteCookies("SESSION")
            }
            .sessionManagement { sessionManagement ->
                sessionManagement.sessionFixation { it.migrateSession() }  // 세션 고정 보호
                sessionManagement.maximumSessions(1)  // 동시 세션 1개로 제한
            }
            // JWT 기반 인증 필터 추가
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}
