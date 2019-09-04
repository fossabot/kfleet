package io.kfleet.monitoring

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kfleet.domain.Traveler
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KTable
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.binder.kafka.streams.InteractiveQueryService
import org.springframework.stereotype.Repository


const val TRAVELER_STORE = "all-travelers"
const val TRAVELER_STATE_STORE = "travelers_by_state"

interface TravelerBinding {
    @Input("travelers")
    fun inputTravelers(): KTable<String, String>
}

@Repository
@EnableBinding(TravelerBinding::class)
class TravelerRepository {


    @Autowired
    lateinit var interactiveQueryService: InteractiveQueryService

    val mapper = jacksonObjectMapper()

    @StreamListener
    fun travelerStateUpdates(@Input("travelers") travelerTable: KTable<String, String>) {
        travelerTable.groupBy { _: String, rawTraveler: String ->
            val traveler: Traveler = mapper.readValue(rawTraveler)
            KeyValue(traveler.state.toString(), "")
        }
                .count(Materialized.`as`(TRAVELER_STATE_STORE))
                .toStream()
                .foreach { a: String, c: Long ->
                    println("$a -> $c")
                }
    }

    fun allTravelersStore(): ReadOnlyKeyValueStore<String, String> = interactiveQueryService
            .getQueryableStore(TRAVELER_STORE, QueryableStoreTypes.keyValueStore<String, String>())


    fun allTravelersStateStore(): ReadOnlyKeyValueStore<String, Long> = interactiveQueryService
            .getQueryableStore(TRAVELER_STATE_STORE, QueryableStoreTypes.keyValueStore<String, Long>())


}

