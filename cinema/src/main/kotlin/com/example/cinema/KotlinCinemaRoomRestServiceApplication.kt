package com.example.cinema

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.*
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@SpringBootApplication
class KotlinCinemaRoomRestServiceApplication

data class Seat(val row: Int, val column: Int, val price: Int)
data class TicketResponse(val token: String, val ticket: Seat)
data class Token(@JsonProperty("token") val value: String)
data class Cinema(val total_rows: Int, val total_columns: Int) {
    val available_seats = Array(total_rows) { row ->
        Array(total_columns) { col ->
            Seat(row + 1, col + 1, if (row + 1 < 5) 10 else 8)
        }
    }.flatten()
}

@RestController
class Controller {
    private var cinema = Cinema(9, 9)
    private val purchasedSeats = mutableListOf<TicketResponse>()
    private val status400 = HttpStatus.BAD_REQUEST.value()
    private val status401 = HttpStatus.UNAUTHORIZED.value()

    @ResponseStatus
    class SeatException(val text: String, val path: String, val status: Int) : RuntimeException(text)

    @ExceptionHandler(SeatException::class)
    fun handleSeatException(ex: SeatException): ResponseEntity<Any> {
        val errorResponse = mapOf("timestamp" to Date(), "status" to ex.status, "error" to ex.text, "path" to ex.path)
        return ResponseEntity.status(ex.status).body(errorResponse)
    }

    @GetMapping("/seats")
    fun getSeats() = cinema

    @PostMapping("/purchase")
    fun setSeats(@RequestBody seat: Seat, request: HttpServletRequest): TicketResponse {
        val chosenSeat = cinema.available_seats.firstOrNull { it.row == seat.row && it.column == seat.column }
            ?: throw SeatException("The number of a row or a column is out of bounds!", request.requestURI, status400)
        val possibleTicket = TicketResponse(UUID.randomUUID().toString(), chosenSeat)
        when (purchasedSeats.find { it.ticket == chosenSeat }) {
            null -> purchasedSeats.add(possibleTicket)
            else -> throw SeatException("The ticket has been already purchased!", request.requestURI, status400)
        }
        return possibleTicket
    }

    @PostMapping("/return")
    fun returnTicket(@RequestBody token: Token, request: HttpServletRequest): Map<String, Seat> {
        lateinit var returnedTicket: Seat
        when (val ticketResponse = purchasedSeats.find { it.token == token.value }) {
            null -> throw SeatException("Wrong token!", request.requestURI, status400)
            else -> returnedTicket = ticketResponse.ticket.also { purchasedSeats.remove(ticketResponse) }
        }
        return mapOf("returned_ticket" to returnedTicket)
    }

    @PostMapping("/stats")
    fun getStats(@RequestParam(required = false) password: String?, request: HttpServletRequest): Map<String, Int> {
        if (password != "super_secret") {
            throw SeatException("The password is wrong!", request.requestURI, status401)
        }
        return mapOf(
            "current_income" to purchasedSeats.sumOf { it.ticket.price },
            "number_of_available_seats" to 81 - purchasedSeats.size,
            "number_of_purchased_tickets" to purchasedSeats.size
        )
    }
}

fun main(args: Array<String>) {
    runApplication<KotlinCinemaRoomRestServiceApplication>(*args)
}