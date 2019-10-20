package io.kfleet.traveler.service.web


import io.kfleet.commands.CommandResponse
import io.kfleet.commands.CommandStatus
import io.kfleet.traveler.service.domain.Traveler
import io.kfleet.traveler.service.domain.createTravelerCommand
import io.kfleet.traveler.service.domain.deleteTravelerCommand
import io.kfleet.traveler.service.domain.traveler
import io.kfleet.traveler.service.repos.CommandsResponseRepository
import io.kfleet.traveler.service.repos.CreateTravelerParams
import io.kfleet.traveler.service.repos.DeleteTravelerParams
import io.kfleet.traveler.service.repos.TravelerRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.expect

@WebFluxTest(TravelerService::class)
@Import(TravelerRoutes::class)
@AutoConfigureWebTestClient(timeout = "15001") // backof retry is between 1 and 3 seconds; 5 times
class TravelerServiceTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var repo: TravelerRepository

    @MockBean
    private lateinit var commandResponseRepo: CommandsResponseRepository

    @Test
    fun travelerByIdTest() {
        val traveler = traveler {
            id = "1"
            name = "testname"
            email = "a@a.com"
        }

        BDDMockito.given(repo.findById("1")).willReturn(Mono.just(traveler))

        val response = webClient.get().uri("/traveler/1")
                .exchange()
                .expectStatus().isOk
                .expectBody(Traveler::class.java)
                .returnResult()

        expect(traveler) { response.responseBody }
    }

    @Test
    fun travelerByIdTest404() {

        BDDMockito.given(repo.findById("1")).willReturn(Mono.error(RuntimeException()))

        webClient.get().uri("/traveler/1")
                .exchange()
                .expectStatus().isNotFound
    }

    @Test
    fun createTraveler() {

        val params = CreateTravelerParams(
                travelerId = "1",
                travelerName = "testName",
                travelerEmail = "a@a.com"
        )
        val traveler = traveler {
            id = "1"
            name = "testName"
            email = "a@a.com"
        }
        val createTravelerCommand = createTravelerCommand {
            commandId = "c1"
            travelerId = "1"
            name = "testName"
            email = "a@a.com"
        }

        BDDMockito
                .given(repo.submitCreateTravelerCommand(params))
                .willReturn(Mono.just(createTravelerCommand))

        BDDMockito
                .given(commandResponseRepo.findCommandResponse(createTravelerCommand.getCommandId()))
                .willReturn(Mono.just(CommandResponse(createTravelerCommand.getCommandId(), createTravelerCommand.getTravelerId(), CommandStatus.SUCCEEDED, null)))

        BDDMockito.given(repo.findById(traveler.getId())).willReturn(Mono.just(traveler))


        val response = webClient.post().uri("/traveler/1/testName/a@a.com")
                .exchange()
                .expectStatus().isCreated
                .expectBody(Traveler::class.java)
                .returnResult()

        expect("1") { response.responseBody!!.getId() }
        expect(traveler) { response.responseBody }
    }

    @Test
    fun createTravelerBadRequest() {
        val result = webClient.post().uri("/traveler/1/ /a@a.com")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody(String::class.java)
                .returnResult()
        assertEquals("travelerName invalid", result.responseBody)
    }

    @Test
    fun createTravelerBadRequestTravelerExists() {
        val params = CreateTravelerParams(
                travelerId = "1",
                travelerName = "testName",
                travelerEmail = "a@a.com")

        val createTravelerCommand = createTravelerCommand {
            commandId = "c1"
            travelerId = "1"
            name = "testName"
            email = "a@a.com"
        }

        BDDMockito
                .given(repo.submitCreateTravelerCommand(params))
                .willReturn(Mono.just(createTravelerCommand))

        BDDMockito
                .given(commandResponseRepo.findCommandResponse(createTravelerCommand.getCommandId()))
                .willReturn(Mono.just(CommandResponse(createTravelerCommand.getCommandId(), createTravelerCommand.getTravelerId(), CommandStatus.REJECTED, "exists")))


        val response = webClient.post().uri("/traveler/1/testName/a@a.com")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody(String::class.java)
                .returnResult()

        expect("exists") { response.responseBody }
    }

    @Test
    fun deleteTraveler() {

        val params = DeleteTravelerParams(travelerId = "1")
        val deleteTravelerCommand = deleteTravelerCommand {
            commandId = "c1"
            travelerId = "1"
        }

        BDDMockito
                .given(repo.submitDeleteTravelerCommand(params))
                .willReturn(Mono.just(deleteTravelerCommand))

        BDDMockito
                .given(commandResponseRepo.findCommandResponse(deleteTravelerCommand.getCommandId()))
                .willReturn(Mono.just(CommandResponse(deleteTravelerCommand.getCommandId(), deleteTravelerCommand.getTravelerId(), CommandStatus.SUCCEEDED, null)))

        webClient.delete().uri("/traveler/1")
                .exchange()
                .expectStatus().isNoContent

    }

    @Test
    fun deleteTravelerBadRequestTravelerDidNotExists() {
        val params = DeleteTravelerParams(travelerId = "1")
        val deleteTravelerCommand = deleteTravelerCommand {
            commandId = "c1"
            travelerId = "1"
        }

        BDDMockito
                .given(repo.submitDeleteTravelerCommand(params))
                .willReturn(Mono.just(deleteTravelerCommand))

        BDDMockito
                .given(commandResponseRepo.findCommandResponse(deleteTravelerCommand.getCommandId()))
                .willReturn(Mono.just(CommandResponse(deleteTravelerCommand.getCommandId(), deleteTravelerCommand.getTravelerId(), CommandStatus.REJECTED, "did not exist")))


        val response = webClient.delete().uri("/traveler/1")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody(String::class.java)
                .returnResult()

        expect("did not exist") { response.responseBody }
    }

    @Test
    fun deleteTravelerBadRequest() {
        val result = webClient.delete().uri("/traveler/ ")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody(String::class.java)
                .returnResult()
        assertEquals("travelerId invalid", result.responseBody)
    }


}