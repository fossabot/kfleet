package io.kfleet.simulation

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class Topics {

    @Bean
    fun ridersTopic() =
        NewTopic("riders", 3, 1)
            .configs(
                mapOf(TopicConfig.CLEANUP_POLICY_CONFIG to TopicConfig.CLEANUP_POLICY_COMPACT)
            )

    @Bean
    fun carsTopic() =
        NewTopic("cars", 3, 1)
            .configs(
                mapOf(TopicConfig.CLEANUP_POLICY_CONFIG to TopicConfig.CLEANUP_POLICY_COMPACT)
            )
}
