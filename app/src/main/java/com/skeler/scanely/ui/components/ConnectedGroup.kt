package com.skeler.scanely.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GroupEndRadius = 26.dp
private val GroupJoinRadius = 6.dp

enum class GroupPosition { Solo, Top, Middle, Bottom }

fun groupPositionOf(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.Solo
    index == 0 -> GroupPosition.Top
    index == count - 1 -> GroupPosition.Bottom
    else -> GroupPosition.Middle
}

@Composable
fun ConnectedGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

/** Corners swap between the group's outer and inner radii while pressed. */
@Composable
private fun connectedShape(position: GroupPosition, pressed: Boolean): RoundedCornerShape {
    val bounce = spring<Dp>(dampingRatio = Spring.DampingRatioLowBouncy)
    val endRadius by animateDpAsState(
        targetValue = if (pressed) GroupJoinRadius else GroupEndRadius,
        animationSpec = bounce,
        label = "tileEndRadius"
    )
    val joinRadius by animateDpAsState(
        targetValue = if (pressed) GroupEndRadius else GroupJoinRadius,
        animationSpec = bounce,
        label = "tileJoinRadius"
    )

    val top = position == GroupPosition.Top || position == GroupPosition.Solo
    val bottom = position == GroupPosition.Bottom || position == GroupPosition.Solo

    return RoundedCornerShape(
        topStart = if (top) endRadius else joinRadius,
        topEnd = if (top) endRadius else joinRadius,
        bottomStart = if (bottom) endRadius else joinRadius,
        bottomEnd = if (bottom) endRadius else joinRadius
    )
}

@Composable
fun ConnectedTile(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = connectedShape(position, pressed)

    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.38f)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            shape = shape,
            color = color,
            interactionSource = interactionSource,
            content = body
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            content = body
        )
    }
}
