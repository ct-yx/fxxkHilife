package com.freebuds.controller.data

import com.freebuds.controller.adapter.huawei.HuaweiOpenFreebudsAdapter
import com.freebuds.controller.core.adapter.EarbudAdapterRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRegressionPlanTest {
    @Test
    fun managerRuntimeFollowUpTargetsOnlyTheAffectedTwentyOperations() {
        assertEquals(
            listOf(RegressionScenario.F to 10),
            BluetoothRegressionRunner.STATE_RETRY_20_SCENARIO_ROUNDS.entries.map { it.key to it.value },
        )
        assertEquals(
            20,
            BluetoothRegressionRunner.operationCount(RegressionProfile.BT_MANAGER_RUNTIME_20, 10),
        )
    }

    @Test
    fun bt4ContractProfileUsesFiveTargetedReads() {
        assertEquals(
            5,
            BluetoothRegressionRunner.operationCount(RegressionProfile.BT4_STATE_CONTRACT_5, 10),
        )
        assertEquals(
            BluetoothRegressionRunner.BT4_STATE_CONTRACT_ROUNDS,
            BluetoothRegressionRunner.operationCount(RegressionProfile.BT4_STATE_CONTRACT_5, 1),
        )
    }

    @Test
    fun previousStateRetryProfileRemainsAvailableForHistoricalReports() {
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

    @Test
    fun bt4ReportRequiresAllFiveNamedContractRounds() {
        val features = (1..5).map { round ->
            RegressionFeatureCheck(
                name = BluetoothRegressionRunner.BT4_STATE_CONTRACT_NAME,
                iteration = round,
                result = RegressionResult.PASS,
                elapsedMs = 1L,
                detail = "ok",
            )
        }
        assertTrue(
            BluetoothRegressionRunner.reportValidationIssues(
                profile = RegressionProfile.BT4_STATE_CONTRACT_5,
                expectedOperations = 5,
                attempts = emptyList(),
                features = features,
                earlyFailure = null,
            ).isEmpty()
        )
        assertTrue(
            BluetoothRegressionRunner.reportValidationIssues(
                profile = RegressionProfile.BT4_STATE_CONTRACT_5,
                expectedOperations = 5,
                attempts = emptyList(),
                features = features.dropLast(1),
                earlyFailure = null,
            ).isNotEmpty()
        )
    }

    @Test
    fun bt4ReportTreatsSkippedAndFailedRoundsAsInvalid() {
        val skipped = (1..5).map { round ->
            RegressionFeatureCheck(
                name = BluetoothRegressionRunner.BT4_STATE_CONTRACT_NAME,
                iteration = round,
                result = if (round == 3) RegressionResult.SKIPPED else RegressionResult.PASS,
                elapsedMs = 1L,
                detail = "round=$round",
            )
        }

        val skippedIssues = BluetoothRegressionRunner.reportValidationIssues(
            profile = RegressionProfile.BT4_STATE_CONTRACT_5,
            expectedOperations = 5,
            attempts = emptyList(),
            features = skipped,
            earlyFailure = null,
        )
        assertTrue(skippedIssues.any { it.contains("SKIPPED") })

        val failedIssues = BluetoothRegressionRunner.reportValidationIssues(
            profile = RegressionProfile.BT4_STATE_CONTRACT_5,
            expectedOperations = 5,
            attempts = emptyList(),
            features = skipped.map { it.copy(result = if (it.iteration == 4) RegressionResult.FAIL else it.result) },
            earlyFailure = null,
        )
        assertTrue(failedIssues.any { it.contains("FAIL") })
    }

    @Test
    fun unknownHuaweiNamesAreNotAcceptedAsKnownAdapterModels() {
        assertTrue(HuaweiOpenFreebudsAdapter.isHuaweiOrHonorName("HUAWEI Mystery Buds"))
        assertTrue(!HuaweiOpenFreebudsAdapter.isKnownModelName("HUAWEI Mystery Buds"))
        assertTrue(HuaweiOpenFreebudsAdapter.isKnownModelName("HUAWEI FreeBuds 6i"))
    }

    @Test
    fun repositoryRegistersTheBuiltInAdapterThroughTheSharedRegistry() {
        DeviceRepository()

        assertTrue(
            EarbudAdapterRegistry.all().any { it.id == HuaweiOpenFreebudsAdapter.id },
        )
    }
}
