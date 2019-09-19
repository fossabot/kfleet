package io.kfleet.cars.service.repos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kfleet.cars.service.domain.Car
import io.kfleet.cars.service.events.CarEvent
import io.kfleet.cars.service.processors.CarStateCountProcessor
import io.kfleet.cars.service.processors.CarStateCountProcessorBinding
import io.kfleet.common.headers
import mu.KotlinLogging
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.StreamsMetadata
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.Output
import org.springframework.cloud.stream.binder.kafka.streams.InteractiveQueryService
import org.springframework.context.ApplicationContext
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Repository
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.*

private val logger = KotlinLogging.logger {}

private const val CARS_RPC = "cars-rpc"

interface ICarsRepository {
    fun findAllCars(): Flux<Car>
    fun findById(id: String): Mono<Car>
    fun getCarsStateCounts(): Mono<Map<String, Long>>
    fun findByIdLocal(id: String): Mono<Car>
    fun findAllCarsLocal(): Flux<Car>
    fun getLocalCarsStateCounts(): Mono<Map<String, Long>>
}

interface CarsBinding {

    companion object {
        const val CAR_EVENTS = "car_events_out"
    }

    @Output(CAR_EVENTS)
    fun carEvents(): MessageChannel

}

@Repository
@EnableBinding(CarsBinding::class)
class CarsRepository(
        @Autowired val interactiveQueryService: InteractiveQueryService,
        @Autowired @Output(CarsBinding.CAR_EVENTS) val outputCarEvents: MessageChannel,
        @Autowired val mapper: ObjectMapper,
        @Autowired val context: ApplicationContext
) : ICarsRepository {

    override fun findAllCars(): Flux<Car> {
        return createCurrentStreamsMetadataAsFlux()
                .map { arrayOfStreamsMetadata: Array<StreamsMetadata> ->
                    arrayOfStreamsMetadata.map {
                        if (it.hostInfo() == interactiveQueryService.currentHostInfo) {
                            logger.debug { "find cars local ${it.port()}" }
                            findAllCarsLocal()
                        } else {
                            logger.debug { "find cars remote per rpc ${it.port()}" }
                            val webClient = WebClient.create("http://${it.host()}:${it.port()}")
                            webClient.get().uri("/$CARS_RPC/")
                                    .retrieve()
                                    .bodyToFlux(String::class.java)
                                    .flatMap { rawCars -> mapper.readValue<List<Car>>(rawCars).toList().toFlux() }
                        }
                    }
                }.flatMap {
                    Flux.create<Car> { sink ->
                        val result = mutableListOf<Car>()
                        Flux.concat(it).subscribe(
                                { result.add(it) },
                                { error -> sink.error(error) },
                                {
                                    result.forEach { sink.next(it) }
                                    sink.complete()
                                })
                    }
                }
    }

    override fun findAllCarsLocal(): Flux<Car> {
        return carsStore().all().use {
            it.asSequence().map { kv -> kv.value }.toList().toFlux()
        }
    }

    override fun findById(id: String): Mono<Car> {
        val hostInfo = interactiveQueryService.getHostInfo(CarStateCountProcessorBinding.CAR_STORE, id, StringSerializer())

        if (hostInfo == interactiveQueryService.currentHostInfo) {
            return findByIdLocal(id)
        }

        val webClient = WebClient.create("http://${hostInfo.host()}:${hostInfo.port()}")
        return webClient.get().uri("/$CARS_RPC/$id")
                .retrieve()
                .bodyToMono(String::class.java)
                .map { mapper.readValue<Car>(it) }
                .log()
    }

    override fun findByIdLocal(id: String): Mono<Car> {
        return carsStore().get(id)?.toMono() ?: Mono.error(Exception("car with id: $id not found"))
    }

    override fun getCarsStateCounts(): Mono<Map<String, Long>> {
        return createCurrentStreamsMetadataAsFlux()
                .map { arrayOfStreamsMetadata: Array<StreamsMetadata> ->
                    arrayOfStreamsMetadata.map {
                        if (it.hostInfo() == interactiveQueryService.currentHostInfo) {
                            logger.debug { "find cars stats local ${it.port()}" }
                            getLocalCarsStateCounts()
                        } else {
                            logger.debug { "find cars stats remote per rpc ${it.port()}" }
                            val webClient = WebClient.create("http://${it.host()}:${it.port()}")
                            webClient.get().uri("/$CARS_RPC/stats")
                                    .retrieve()
                                    .bodyToMono(String::class.java)
                                    .flatMap { rawCars -> mapper.readValue<Map<String, Long>>(rawCars).toMono() }
                        }
                    }
                }.flatMap {
                    Mono.create { sink: MonoSink<Map<String, Long>> ->
                        val result = mutableMapOf<String, Long>()
                        Flux.concat(it).subscribe(
                                { result.putAll(it) },
                                { error -> sink.error(error) },
                                { sink.success(result) })
                    }
                }.next()
    }

    override fun getLocalCarsStateCounts(): Mono<Map<String, Long>> {
        return carStateStore().all().use { allCars ->
            allCars.asSequence().map { it.key to it.value }.toMap()
        }.toMono()
    }

    private fun getKafakStreams(): KafkaStreams {
        val beanNameCreatedBySpring = "&stream-builder-${CarStateCountProcessor::carStateUpdates.name}"
        val streamsBuilderFactoryBean = context.getBean(beanNameCreatedBySpring, StreamsBuilderFactoryBean::class.java)
        return streamsBuilderFactoryBean.kafkaStreams!!
    }

    private fun createCurrentStreamsMetadataAsFlux() = Flux.create { sink: FluxSink<Array<StreamsMetadata>> ->
        val streamMetadata = getKafakStreams().allMetadataForStore(CarStateCountProcessorBinding.CAR_STORE)
        sink.next(streamMetadata.toTypedArray())
        sink.complete()
    }

    private fun carsStore(): ReadOnlyKeyValueStore<String, Car> = interactiveQueryService
            .getQueryableStore(CarStateCountProcessorBinding.CAR_STORE, QueryableStoreTypes.keyValueStore<String, Car>())


    private fun carStateStore(): ReadOnlyKeyValueStore<String, Long> = interactiveQueryService
            .getQueryableStore(CarStateCountProcessorBinding.CAR_STATE_STORE, QueryableStoreTypes.keyValueStore<String, Long>())

    fun publishCarEvents(event: CarEvent) {
        val msg = MessageBuilder.createMessage(event, headers(event.id))
        outputCarEvents.send(msg)
    }
}
