package br.com.ajadigital.core.shared.adapters.out.serialization

import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class JacksonJsonSerializer(private val mapper: ObjectMapper) : JsonSerializerOutputPort {
    override fun serialize(request: SerializeJsonDto): ByteArray = mapper.writeValueAsBytes(request.value)
}
