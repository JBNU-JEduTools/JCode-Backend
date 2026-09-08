package org.jbnu.jdevops.jcodeportallogin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class SchedulingConfig {
    @Bean("courseManagerCacheScheduler")
    fun courseManagerCacheScheduler() = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("course-manager-cache-")
        setWaitForTasksToCompleteOnShutdown(false)
    }
}
