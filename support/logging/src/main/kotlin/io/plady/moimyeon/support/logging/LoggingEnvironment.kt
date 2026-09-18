package io.plady.moimyeon.support.logging

enum class LoggingEnvironment(
    val profile: String,
) {
    LOCAL("local"),
    LOCAL_DEV("local-dev"),
    TEST("test"),
    DEV("dev"),
    STAGING("staging"),
    LIVE("live"),
    ;

    companion object {
        fun from(profiles: Set<String>): LoggingEnvironment {
            val environments = entries.filter { it.profile in profiles }.toMutableSet()
            if (TEST in environments) environments.remove(LOCAL)
            require(environments.size <= 1) { "Conflicting logging environments" }
            if (environments.isEmpty()) {
                require(profiles.isEmpty() || profiles == setOf("default")) { "A base logging environment is required" }
                return LOCAL
            }
            return environments.single()
        }
    }
}
