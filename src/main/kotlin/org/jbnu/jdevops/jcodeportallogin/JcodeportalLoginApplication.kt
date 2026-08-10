package org.jbnu.jdevops.jcodeportallogin

import org.jbnu.jdevops.jcodeportallogin.config.OpenApiConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

@Import(OpenApiConfig::class)  // Swagger Custom 설정 (title, authentication ...)

@SpringBootApplication
@EnableScheduling
class JcodeportalLoginApplication {
    companion object {
        @JvmStatic     // Java의 정정 메소드로 main 생성
        fun main(args: Array<String>) {
            runApplication<JcodeportalLoginApplication>(*args)
        }
    }
}
