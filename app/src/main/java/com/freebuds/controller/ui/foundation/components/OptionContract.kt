package com.freebuds.controller.ui.foundation.components

import androidx.compose.runtime.Immutable

/** A small typed contract shared by settings rows and device feature controls. */
@Immutable
data class OptionUiState<T>(
    val selectedValue: T? = null,
    val pendingValue: T? = null,
    val options: List<UiOption<T>> = emptyList(),
    val unavailableReason: String? = null,
) {
    val isPending: Boolean get() = pendingValue != null
    val isAvailable: Boolean get() = unavailableReason == null && options.isNotEmpty()
}

fun interface UiTextMapper<in T> {
    fun text(value: T): String
}

/** Builds option rows without letting pages duplicate selected/pending semantics. */
object OptionPresenter {
    fun <T> present(
        values: List<T>,
        selectedValue: T?,
        pendingValue: T? = null,
        mapper: UiTextMapper<T>,
        enabled: Boolean = true,
        unavailableReason: String? = null,
    ): OptionUiState<T> = OptionUiState(
        selectedValue = selectedValue,
        pendingValue = pendingValue,
        options = values.distinct().map { value ->
            UiOption(
                value = value,
                label = mapper.text(value),
                enabled = enabled && unavailableReason == null,
                selected = value == selectedValue,
                pending = value == pendingValue,
                unavailableReason = unavailableReason,
            )
        },
        unavailableReason = unavailableReason,
    )
}
