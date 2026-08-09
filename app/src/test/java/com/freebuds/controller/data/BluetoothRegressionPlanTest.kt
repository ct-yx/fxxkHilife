package com.freebuds.controller.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothRegressionPlanTest {
    @Test
    fun stateRetryFollowUpTargetsOnlyTheAffectedTwentyOperations() {
        assertEquals(
            listOf(RegressionScenario.F to 10),
            BluetoothRegressionRunner.STATE_RETRY_20_SCENARIO_ROUNDS.entries.map { it.key to it.value },
        )
        assertEquals(
            20,
            BluetoothRegressionRunner.operationCount(RegressionProfile.BT_STATE_RETRY_20, 10),
        )
    }

    @Test
    fun targetedPlanKeepsTheRequested36Operations() {
        assertEquals(
            listOf(
                RegressionScenario.B to 10,
                RegressionScenario.F to 10,
                RegressionScenario.D to 3,
                RegressionScenario.E to 3,
            ),
            BluetoothRegressionRunner.TARGETED_36_SCENARIO_ROUNDS.entries.map { it.key to it.value },
        )
        assertEquals(
            36,
            BluetoothRegressionRunner.operationCount(RegressionProfile.BT_TARGETED_36, 10),
        )
    }

    @Test
    fun otherProfilesKeepTheirIterationSemantics() {
        assertEquals(
            10,
            BluetoothRegressionRunner.operationCount(RegressionProfile.ANC_WEAR_STATE, 10),
        )
        assertEquals(
            100,
            BluetoothRegressionRunner.operationCount(RegressionProfile.FULL_MATRIX, 10),
        )
    }
}
