package pt.isel.daw.pokerdice.model.output

import org.springframework.util.MultiValueMap
import pt.isel.daw.pokerdice.Uris

data class ApiResponse<T>(
    val data: T,
    val meta: Meta? = null,
)

data class Meta(
    val apiVersion: String = Uris.VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String? = null,
    val params: MultiValueMap<String, String?>? = null,
)
