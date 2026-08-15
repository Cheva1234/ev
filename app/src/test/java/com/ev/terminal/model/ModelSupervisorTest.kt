package com.ev.terminal.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSupervisorTest {

    @Test
    fun `failed inference remains in error state instead of ready`() {
        assertEquals(ModelState.READY, modelStateAfterTask(succeeded = true))
        assertEquals(ModelState.ERROR, modelStateAfterTask(succeeded = false))
    }
}
