package com.authrisk.infrastructure.persistence

import com.authrisk.application.port.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/** Implementa [TransactionRunner] con la transacción JPA de Spring. */
@Component
class SpringTransactionRunner(private val template: TransactionTemplate) : TransactionRunner {
    @Suppress("UNCHECKED_CAST")
    override fun <T> inTransaction(block: () -> T): T = template.execute { block() } as T
}
