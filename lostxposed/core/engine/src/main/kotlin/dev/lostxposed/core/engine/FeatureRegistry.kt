package dev.lostxposed.core.engine

import dev.lostxposed.core.api.FeatureDescriptor
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.InjectionFactory

/**
 * Every feature ships in the APK and registers here at startup. There is deliberately no
 * plugin or download mechanism: downloadable hook code injected into system_server would be
 * a malware distribution channel.
 */
class FeatureRegistry private constructor(val registrations: List<Registration>) {

    data class Registration(
        val descriptor: FeatureDescriptor,
        val factory: InjectionFactory,
    )

    fun find(id: FeatureId): Registration? = registrations.firstOrNull { it.descriptor.id == id }

    class Builder {
        private val factories = mutableListOf<InjectionFactory>()

        fun register(factory: InjectionFactory) = apply { factories += factory }

        fun build(): FeatureRegistry {
            // Descriptors are read once here rather than per injection. Construction must
            // stay cheap and side-effect free.
            val registrations = factories.map { Registration(it.create().descriptor, it) }
            val duplicates = registrations.groupBy { it.descriptor.id }.filterValues { it.size > 1 }
            require(duplicates.isEmpty()) { "duplicate feature ids: ${duplicates.keys}" }
            return FeatureRegistry(registrations)
        }
    }

    companion object {
        fun build(block: Builder.() -> Unit): FeatureRegistry = Builder().apply(block).build()
    }
}
