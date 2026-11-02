package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.port.IdGenerator

/** Hands out `id-1`, `id-2`, ... */
class FakeIdGenerator(
    private val prefix: String = "id-",
) : IdGenerator {
    private var counter = 0

    override fun generate(): String {
        counter += 1
        return "$prefix$counter"
    }
}
