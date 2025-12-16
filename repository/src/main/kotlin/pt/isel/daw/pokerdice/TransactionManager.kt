package pt.isel.daw.pokerdice

interface TransactionManager {
    fun <R> run(block: (Transaction) -> R): R
}
