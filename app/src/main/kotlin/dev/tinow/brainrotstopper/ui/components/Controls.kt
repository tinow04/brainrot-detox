package dev.tinow.brainrotstopper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.tinow.brainrotstopper.debug.debugBounds
import dev.tinow.brainrotstopper.ui.theme.BsDims
import dev.tinow.brainrotstopper.ui.theme.BsTheme

private val Shape = RoundedCornerShape(BsDims.Radius)

/** `.sw`: 48x28 checkbox-like switch. Purely visual when [onCheckedChange] is null (the row carries the semantics). */
@Composable
fun BsSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val semantic = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Box(
        modifier
            .then(semantic)
            .size(BsDims.SwitchWidth, BsDims.SwitchHeight)
            .background(if (checked) c.accent else Color.Transparent, Shape)
            .border(BsDims.Border, if (checked) c.accent else c.ctl, Shape),
    ) {
        Box(
            Modifier
                .offset(x = if (checked) BsDims.SwitchKnobOnLeft else BsDims.SwitchKnobInset, y = BsDims.SwitchKnobInset)
                .size(BsDims.SwitchKnob)
                .clip(RoundedCornerShape(2.dp))
                .background(if (checked) c.onAccent else c.ctl),
        )
    }
}

/** `.step`: minus, value, plus in one bordered group. */
@Composable
fun BsStepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, minusLabel: String = "weniger", plusLabel: String = "mehr") {
    val c = BsTheme.colors
    val t = BsTheme.type
    // `.step` is auto-height: the 1 px border adds to the 48 dp buttons (outer height 50 dp), Compose border() takes no layout space.
    Row(modifier.border(BsDims.Border, c.ctl, Shape).padding(BsDims.Border), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", minusLabel, onMinus)
        Box(Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.Center) { BsText(value.toString(), t.stat.copy(fontSize = 20.sp), c.text) }
        StepButton("+", plusLabel, onPlus)
    }
}

@Composable
private fun StepButton(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(BsDims.StepHeight)
            .debugBounds(description)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { BsText(glyph, BsTheme.type.card, BsTheme.colors.text) }
}

/** `.segs`: segmented single-choice control, overlapping 1 dp borders, selected segment filled with the accent. */
@Composable
fun BsSegments(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy((-1).dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(
                topStart = if (i == 0) BsDims.Radius else 0.dp,
                bottomStart = if (i == 0) BsDims.Radius else 0.dp,
                topEnd = if (i == options.lastIndex) BsDims.Radius else 0.dp,
                bottomEnd = if (i == options.lastIndex) BsDims.Radius else 0.dp,
            )
            Box(
                Modifier
                    .weight(1f)
                    .zIndex(if (on) 1f else 0f)
                    .debugBounds(label)
                    .heightIn(min = BsDims.SegsHeight)
                    .background(if (on) c.accent else Color.Transparent, shape)
                    .border(BsDims.Border, if (on) c.accent else c.ctl, shape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                BsText(label, if (on) t.body.copy(fontWeight = FontWeight.Bold) else t.body, if (on) c.onAccent else c.text)
            }
        }
    }
}

/** `.opt`: `(•)`/`( )` single-choice row with a dim count on the right. */
@Composable
fun BsRadioOption(selected: Boolean, label: String, count: String?, onClick: () -> Unit, modifier: Modifier = Modifier, divider: Boolean = true) {
    val c = BsTheme.colors
    val t = BsTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .debugBounds(label)
            .heightIn(min = BsDims.OptHeight)
            .drawBehind {
                if (divider) drawRect(c.line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
            }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(BsDims.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp).clearAndSetSemantics { }) { BsText(if (selected) "(•)" else "( )", t.body.copy(fontWeight = FontWeight.Bold), c.accent) }
        BsText(label, t.body, c.text, Modifier.weight(1f))
        if (count != null) BsText(count, t.label, c.dim)
    }
}

/** `.nav`: bottom navigation, selected item with accent top line and `> ` prefix. */
@Composable
fun BsNavBar(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, label: String = "hauptmenü") {
    val c = BsTheme.colors
    val t = BsTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .background(c.bg2)
            .drawBehind { drawRect(c.line, Offset.Zero, Size(size.width, 1.dp.toPx())) }
            .padding(bottom = BsDims.NavBottom)
            .selectableGroup()
            .semantics { contentDescription = label },
    ) {
        items.forEachIndexed { i, text ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .debugBounds("nav-$text")
                    .heightIn(min = BsDims.NavHeight)
                    .drawBehind {
                        if (on) drawRect(c.accent, Offset(0f, -1.dp.toPx()), Size(size.width, 2.dp.toPx()))
                    }
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                BsText(if (on) "> $text" else text, if (on) t.navOn else t.meta.copy(fontWeight = FontWeight.Medium), if (on) c.accent else c.muted)
            }
        }
    }
}

/** `.search`: prompt `$ grep` plus a text field; border turns accent on focus. */
@Composable
fun BsSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, prompt: String, modifier: Modifier = Modifier, description: String = placeholder, password: Boolean = false) {
    val c = BsTheme.colors
    val t = BsTheme.type
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BsDims.SearchHeight)
            .border(BsDims.Border, if (focused) c.accent else c.ctl, Shape)
            .padding(horizontal = BsDims.S12),
        horizontalArrangement = Arrangement.spacedBy(BsDims.SepGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BsText(prompt, t.body, c.accent, Modifier.clearAndSetSemantics { }) // `.search` prompt inherits the 14 px body, only the input is 16 px
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = t.input.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                interactionSource = source,
                keyboardOptions = if (password) KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false) else KeyboardOptions.Default,
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            )
            if (value.isEmpty()) BsText(placeholder, t.input, c.dim, Modifier.clearAndSetSemantics { })
        }
    }
}
