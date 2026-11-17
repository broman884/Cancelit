package com.cancelit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cancelit.app.data.FocusStore
import com.cancelit.app.formatDuration
import com.cancelit.app.ui.AppTextField
import com.cancelit.app.ui.Border
import com.cancelit.app.ui.DotStyle
import com.cancelit.app.ui.GroupCard
import com.cancelit.app.ui.PrimaryButton
import com.cancelit.app.ui.RowDivider
import com.cancelit.app.ui.ScreenHeader
import com.cancelit.app.ui.SecondaryButton
import com.cancelit.app.ui.Section
import com.cancelit.app.ui.SectionCard
import com.cancelit.app.ui.StatusDot
import com.cancelit.app.ui.StyleBody
import com.cancelit.app.ui.StyleCaption
import com.cancelit.app.ui.StyleHeading
import com.cancelit.app.ui.StyleHint
import com.cancelit.app.ui.Surface2
import com.cancelit.app.ui.TextPrimary
import com.cancelit.app.ui.TextTertiary

// PIN lock (called Child Mode in code; shown to users as "PIN lock"). See FocusStore for what
// it freezes and why it's a self-control aid rather than security.

private const val CHILD_PIN_LENGTH = 4

/** The PIN tab: set a PIN to lock the blacklist, keywords and timers; re-enter it to unlock. */
@Composable
internal fun ChildModeScreen(store: FocusStore) {
    var tick by remember { mutableIntStateOf(0) }
    val hasPin = remember(tick) { store.hasChildPin() }
    val locked = remember(tick) { store.childModeOn() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { ScreenHeader("PIN lock", "Lock your settings behind a PIN. Give it to someone you trust to make changing them harder.") }
        item {
            when {
                !hasPin -> ChildModeSetupCard(store) { tick++ }
                locked -> ChildModeLockedCard(store) { tick++ }
                else -> ChildModeUnlockedCard(store) { tick++ }
            }
        }
        item {
            Section("What gets locked") {
                GroupCard {
                    listOf("Blocked apps", "Blocked keywords", "Browser safety", "Time per app", "Lock length").forEachIndexed { index, label ->
                        if (index > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, null,
                                tint = if (locked) TextPrimary else TextTertiary, modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = StyleBody, modifier = Modifier.weight(1f))
                            Text(if (locked) "Locked" else "Editable", style = StyleHint)
                        }
                    }
                }
            }
        }
    }
}

/** A PIN field that's masked while typing, with a row of dots underneath that fills in as
 *  digits are entered — the familiar phone-passcode look. */
@Composable
private fun PinDotsField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = StyleCaption)
        Spacer(Modifier.height(6.dp))
        AppTextField(
            value = value,
            onValueChange = { new -> if (new.length <= CHILD_PIN_LENGTH && new.all { it.isDigit() }) onValueChange(new) },
            placeholder = "••••",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(CHILD_PIN_LENGTH) { i ->
                Box(
                    Modifier.padding(horizontal = 6.dp).size(12.dp).clip(CircleShape)
                        .background(if (i < value.length) TextPrimary else Surface2)
                        .border(1.dp, if (i < value.length) TextPrimary else Border, CircleShape)
                )
            }
        }
    }
}

/** First-time setup, and also reused to change an existing PIN. Either way, confirming sets the
 *  PIN and immediately locks. */
@Composable
private fun ChildModeSetupCard(store: FocusStore, changingExisting: Boolean = false, onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SectionCard {
        Text(if (changingExisting) "Set a new PIN" else "Set a PIN", style = StyleHeading)
        Spacer(Modifier.height(4.dp))
        Text("Choose $CHILD_PIN_LENGTH digits. Your settings lock as soon as you confirm.", style = StyleCaption)
        Spacer(Modifier.height(18.dp))
        PinDotsField(pin, { pin = it; error = null }, "PIN")
        Spacer(Modifier.height(16.dp))
        PinDotsField(confirm, { confirm = it; error = null }, "Confirm PIN")
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = StyleCaption.copy(color = TextPrimary))
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "Set PIN and lock",
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.length == CHILD_PIN_LENGTH && confirm.length == CHILD_PIN_LENGTH
        ) {
            if (pin != confirm) {
                error = "PINs don't match."
                confirm = ""
            } else {
                store.setChildModePin(pin)
                onDone()
            }
        }
    }
}

/** Shown while PIN lock is on: everything is frozen until this PIN is entered correctly. */
@Composable
private fun ChildModeLockedCard(store: FocusStore, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(DotStyle.ACTIVE)
            Spacer(Modifier.width(10.dp))
            Text("PIN lock is on", style = StyleHeading)
        }
        Spacer(Modifier.height(4.dp))
        Text("Enter your PIN to unlock.", style = StyleCaption)
        Spacer(Modifier.height(18.dp))
        PinDotsField(pin, { pin = it; error = null }, "PIN")
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = StyleCaption.copy(color = TextPrimary))
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "Unlock",
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.length == CHILD_PIN_LENGTH
        ) {
            val waitBefore = store.pinRetryAt() - System.currentTimeMillis()
            when {
                waitBefore > 0 -> error = "Too many wrong tries. Try again in ${formatDuration(waitBefore)}."
                store.disableChildMode(pin) -> onUnlocked()
                else -> {
                    val wait = store.pinRetryAt() - System.currentTimeMillis()
                    error = if (wait > 0) "Wrong PIN. Try again in ${formatDuration(wait)}." else "Wrong PIN."
                }
            }
            pin = ""
        }
    }
}

/** Shown when a PIN exists but PIN lock isn't currently locking anything. */
@Composable
private fun ChildModeUnlockedCard(store: FocusStore, onChange: () -> Unit) {
    var changingPin by remember { mutableStateOf(false) }

    if (changingPin) {
        ChildModeSetupCard(store, changingExisting = true) { changingPin = false; onChange() }
        return
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(DotStyle.OFF)
            Spacer(Modifier.width(10.dp))
            Text("PIN lock is off", style = StyleHeading)
        }
        Spacer(Modifier.height(4.dp))
        Text("Your PIN is saved. Turn it back on to lock your settings.", style = StyleCaption)
        Spacer(Modifier.height(18.dp))
        PrimaryButton(text = "Turn on PIN lock", modifier = Modifier.fillMaxWidth()) {
            store.enableChildMode()
            onChange()
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = "Change PIN", modifier = Modifier.fillMaxWidth()) { changingPin = true }
    }
}
