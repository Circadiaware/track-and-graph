/*
* This file is part of Track & Graph
*
* Track & Graph is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Track & Graph is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.samco.trackandgraph.ui.compose.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.samco.trackandgraph.ui.compose.theming.disabledAlpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun LabelInputTextField(
    modifier: Modifier = Modifier,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusManager: FocusManager? = null
) {
    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { onValueChange(it) },
        keyboardActions = KeyboardActions(
            onNext = { focusManager?.moveFocus(FocusDirection.Down) }
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
            capitalization = KeyboardCapitalization.Sentences
        ),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
fun ValueInputTextField(
    modifier: Modifier = Modifier,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusManager: FocusManager? = null,
    focusRequester: FocusRequester? = null
) {
    val focusUpdateScope = rememberCoroutineScope()

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { onValueChange(it) },
        keyboardActions = KeyboardActions(
            onNext = { focusManager?.moveFocus(FocusDirection.Down) }
        ),
        placeholder = {
            Text(
                "1.0",
                color = MaterialTheme.colors.onSurface.copy(
                    alpha = MaterialTheme.colors.disabledAlpha
                )
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        ),
        singleLine = true,
        modifier = modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    focusUpdateScope.launch {
                        delay(20)
                        onValueChange(
                            textFieldValue.copy(
                                selection = TextRange(0, textFieldValue.text.length)
                            )
                        )
                    }
                }
            }
            .let {
                if (focusRequester != null) it.focusRequester(focusRequester)
                else it
            },
    )
}
