package dev.xverlxrd.batterycapacity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.model.SessionState
import dev.xverlxrd.batterycapacity.ui.screens.calibration.CalibrationContent
import dev.xverlxrd.batterycapacity.ui.screens.calibration.CalibrationUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI-тест мастера калибровки: переключение шагов по состоянию сессии. */
@RunWith(AndroidJUnit4::class)
class CalibrationWizardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun session(state: SessionState) = CalibrationSession(
        id = 1L,
        startedAtMs = 0,
        updatedAtMs = 0,
        state = state,
        socAtStart = 100f,
        lowestSoc = 40f,
        lastSoc = 40f,
        collectedDeltaUah = 0,
        sampleCount = 0,
    )

    @Test
    fun intro_showsStartButton() {
        composeRule.setContent {
            CalibrationContent(
                state = CalibrationUiState(session = null),
                reducedMotion = true,
                onStart = {},
                onResume = {},
                onCancel = {},
            )
        }
        composeRule.onNodeWithText("Начать тест").assertIsDisplayed()
    }

    @Test
    fun waitingDischarge_showsStepOneAndThresholdHint() {
        composeRule.setContent {
            CalibrationContent(
                state = CalibrationUiState(session = session(SessionState.WAITING_DISCHARGE)),
                reducedMotion = true,
                onStart = {},
                onResume = {},
                onCancel = {},
            )
        }
        composeRule.onNodeWithText("Шаг 1 · Разряд").assertIsDisplayed()
        composeRule.onNodeWithText("Нужно опуститься ниже 15%").assertIsDisplayed()
    }

    @Test
    fun paused_showsResumeButton_andCancelDialogWorks() {
        composeRule.setContent {
            CalibrationContent(
                state = CalibrationUiState(
                    session = session(SessionState.PAUSED).copy(pauseReason = "charger_unplugged"),
                ),
                reducedMotion = true,
                onStart = {},
                onResume = {},
                onCancel = {},
            )
        }
        composeRule.onNodeWithText("Продолжить").assertIsDisplayed()
        // Отмена через диалог.
        composeRule.onNodeWithText("Прервать тест").performClick()
        composeRule.onNodeWithText("Отменить тест?").assertIsDisplayed()
        composeRule.onNodeWithText("Отменить тест").performClick()
    }
}
