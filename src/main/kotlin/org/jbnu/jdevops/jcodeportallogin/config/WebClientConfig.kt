package org.jbnu.jdevops.jcodeportallogin.config

import org.jbnu.jdevops.jcodeportallogin.service.GeneratorServiceTokenProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.ClientRequest

const val GENERATOR_SCOPE_ATTRIBUTE = "generator.scope"

@Configuration
class WebClientConfig {

    @Value("\${watcher.url}")
    private lateinit var watcherUrl: String

    @Value("\${generator.bootstrap.url}")
    private lateinit var generatorBootstrapUrl: String

    @Value("\${generator.workspace.url}")
    private lateinit var generatorWorkspaceUrl: String

    @Value("\${spring.codec.max-in-memory-size}")
    private lateinit var maxInMemorySize: DataSize

    @Bean
    fun watcherWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(watcherUrl)
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(maxInMemorySize.toBytes().toInt())
            }
            .build()
    }

    private fun generatorClient(
        baseUrl: String,
        tokenProvider: GeneratorServiceTokenProvider,
        scopes: Set<String>
    ): WebClient {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .filter { request, next ->
                val scope = request.attribute(GENERATOR_SCOPE_ATTRIBUTE)
                    .map(Any::toString)
                    .orElseThrow { IllegalStateException("Generator operation scope is required") }
                require(scope in scopes) { "Generator operation scope is not allowed for this controller: $scope" }
                val authenticated = ClientRequest.from(request)
                    .headers { headers -> headers.setBearerAuth(tokenProvider.createToken(setOf(scope))) }
                    .build()
                next.exchange(authenticated)
            }
            .build()
    }

    @Bean
    fun generatorBootstrapWebClient(tokenProvider: GeneratorServiceTokenProvider): WebClient = generatorClient(
        generatorBootstrapUrl,
        tokenProvider,
        setOf("namespace:write", "namespace:delete")
    )

    @Bean
    fun generatorWorkspaceWebClient(tokenProvider: GeneratorServiceTokenProvider): WebClient = generatorClient(
        generatorWorkspaceUrl,
        tokenProvider,
        setOf("namespace:resources:delete", "jcode:write", "jcode:delete", "workspace:write")
    )
}
