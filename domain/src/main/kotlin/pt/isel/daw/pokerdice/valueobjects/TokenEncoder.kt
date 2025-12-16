package pt.isel.daw.pokerdice.valueobjects

interface TokenEncoder {
    fun createValidationInformation(token: String): TokenValidationInfo
}
