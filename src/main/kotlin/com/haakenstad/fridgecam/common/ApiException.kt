package com.haakenstad.fridgecam.common

import org.springframework.http.HttpStatus

/**
 * Kastes fra controllere for feil klienten skal få vite om.
 * [GlobalExceptionHandler] gjør den om til `{ "code": …, "message": … }`.
 *
 * [code] er stabil og ment for kode i frontend; [message] er for mennesker.
 */
class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)
