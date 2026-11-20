package com.authrisk.application.port

/** Ejecuta un bloque dentro de una transacción; si el bloque lanza, se revierte. */
interface TransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}
