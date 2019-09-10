package io.kfleet.simulation.emitter

import io.kfleet.domain.Car
import mu.KotlinLogging
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.Output
import org.springframework.cloud.stream.reactive.StreamEmitter
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import reactor.core.publisher.Flux

private val logger = KotlinLogging.logger {}

@EnableBinding(CarBindings::class)
class CarEmitter {

    @StreamEmitter
    @Output(CarBindings.CARS)
    fun emitCars(): Flux<Message<Car>> = randomDelayFluxer(CAR_COUNT).map {
        val car = Car.create(it)
        logger.debug { "emit: $car" }
        MessageBuilder.createMessage(car, headers(it))
    }


}

interface CarBindings {

    companion object {
        const val CARS = "cars"
    }

    @Output(CARS)
    fun cars(): MessageChannel

}
