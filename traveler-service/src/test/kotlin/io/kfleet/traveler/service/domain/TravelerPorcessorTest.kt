package io.kfleet.traveler.service.domain

import io.kfleet.commands.CommandResponse
import io.kfleet.commands.CommandStatus
import io.kfleet.domain.events.ownerCreatedEvent
import io.kfleet.domain.events.traveler.TravelerCreatedEvent
import io.kfleet.domain.events.traveler.TravelerDeletedEvent
import io.kfleet.traveler.service.processors.CommandAndTraveler
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.expect


class TravelerPorcessorTest {


    private val travelerProcessor = TravelerProcessor()

    @Test
    fun createTravelerRejectedTest() {
        val travelerId = "1"
        val commandId = "1a"

        val command = createTravelerCommand {
            setCommandId(commandId)
            setTravelerId(travelerId)
            name = "test"
            email = "a@a.com"
        }

        val traveler = traveler {
            id = travelerId
            name = "test"
            email = "a@a.com"
        }

        val result = travelerProcessor.processCommand(CommandAndTraveler(command, traveler))

        expect(1) { result.count() }
        expect(commandId) { result[0].key }
        expect(CommandStatus.REJECTED) { (result[0].value as CommandResponse).getStatus() }
        assertNull((result[0].value as CommandResponse).getRessourceId())
        expect("Traveler with id $travelerId already exists") { (result[0].value as CommandResponse).getReason() }
    }

    @Test
    fun createTravelerSucceededTest() {

        val travelerId = "1"
        val travelerName = "test"
        val travelerEmail = "a@a.com"

        val command = createTravelerCommand {
            commandId = "65823"
            setTravelerId(travelerId)
            name = travelerName
            email = travelerEmail
        }

        val traveler = null

        val result = travelerProcessor.processCommand(CommandAndTraveler(command, traveler))

        expect(3, { result.count() })

        val travelerKV = result.filter { it.value is Traveler }
        expect(travelerId) { travelerKV[0].key }
        expect(travelerId) { (travelerKV[0].value as Traveler).getId() }
        expect(travelerName) { (travelerKV[0].value as Traveler).getName() }
        expect(travelerEmail) { (travelerKV[0].value as Traveler).getEmail() }

        val commandKv = result.filter { it.value is CommandResponse }
        assertNotEquals(travelerId, commandKv[0].key)
        expect(CommandStatus.SUCCEEDED) { (commandKv[0].value as CommandResponse).getStatus() }
        expect(travelerId) { (commandKv[0].value as CommandResponse).getRessourceId() }
        assertNull((commandKv[0].value as CommandResponse).getReason())

        val eventKv = result.filter { it.value is TravelerCreatedEvent }
        expect(travelerId) { eventKv[0].key }
        expect(travelerName) { (eventKv[0].value as TravelerCreatedEvent).getName() }
        expect(travelerEmail) { (eventKv[0].value as TravelerCreatedEvent).getEmail() }
    }


    @Test
    fun deleteTravelerRejectedTest() {
        val travelerId = "1"
        val commandId = "1a"

        val command = deleteTravelerCommand {
            setCommandId(commandId)
            setTravelerId(travelerId)
        }

        val result = travelerProcessor.processCommand(CommandAndTraveler(command, null))

        expect(1) { result.count() }
        expect(commandId) { result[0].key }
        expect(CommandStatus.REJECTED) { (result[0].value as CommandResponse).getStatus() }
        assertNull((result[0].value as CommandResponse).getRessourceId())
        expect("Traveler with id $travelerId did not exist") { (result[0].value as CommandResponse).getReason() }
    }

    @Test
    fun deleteTravelerSucceededTest() {

        val travelerId = "1"

        val command = deleteTravelerCommand {
            commandId = "65823"
            setTravelerId(travelerId)
        }

        val traveler = traveler {
            id = travelerId
            name = "name"
            email = "a@a.com"
        }

        val result = travelerProcessor.processCommand(CommandAndTraveler(command, traveler))

        expect(3, { result.count() })

        val travelerKV = result.filter { it.value == null }
        expect(travelerId) { travelerKV[0].key }

        val commandKv = result.filter { it.value is CommandResponse }
        assertNotEquals(travelerId, commandKv[0].key)
        expect(CommandStatus.SUCCEEDED) { (commandKv[0].value as CommandResponse).getStatus() }
        expect(travelerId) { (commandKv[0].value as CommandResponse).getRessourceId() }
        assertNull((commandKv[0].value as CommandResponse).getReason())

        val eventKv = result.filter { it.value is TravelerDeletedEvent }
        expect(travelerId) { eventKv[0].key }
        expect(travelerId) { (eventKv[0].value as TravelerDeletedEvent).getTravelerId() }
    }

    @Test
    fun testUnknownCommandException() {
        // this is not a traveler command! did you see it?
        val command = ownerCreatedEvent {
            ownerId = "1"
            name = " name"
        }
        assertFailsWith(RuntimeException::class) {
            travelerProcessor.processCommand(CommandAndTraveler(command, null))
        }
    }

}