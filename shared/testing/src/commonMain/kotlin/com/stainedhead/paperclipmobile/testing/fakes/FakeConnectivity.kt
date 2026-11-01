package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeConnectivity(
    online: Boolean = true,
) : Connectivity {
    private val online = MutableStateFlow(online)

    override val isOnline: StateFlow<Boolean> = this.online

    fun setOnline(value: Boolean) {
        online.value = value
    }
}
