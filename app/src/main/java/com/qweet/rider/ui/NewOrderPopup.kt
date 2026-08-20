package com.qweet.rider.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qweet.rider.data.OrderOfferDto
import kotlinx.coroutines.delay

/**
 * Full-screen dialog for a new incoming delivery offer, with its own local
 * countdown that auto-declines at zero. Shown from MainTabs (wraps every
 * tab) so it pops up no matter what screen the rider is currently on.
 */
@Composable
fun NewOrderPopup(
    offer: OrderOfferDto,
    actionInFlight: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onTimeout: () -> Unit
) {
    var secondsLeft by remember(offer.delivery_id) { mutableStateOf(offer.seconds_left) }

    LaunchedEffect(offer.delivery_id) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
        onTimeout()
    }

    Dialog(
        onDismissRequest = { /* not dismissible by tapping outside — must accept/decline */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxWidth()) {
                    // Sheet
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp)
                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .padding(top = 28.dp, start = 20.dp, end = 20.dp, bottom = 28.dp)
                    ) {
                        // Header: label + price
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "NEW REQUEST",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${offer.currency_symbol}${"%.0f".format(offer.est_earning)}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        RouteCard(offer)

                        Spacer(Modifier.height(20.dp))

                        // Actions
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onDecline,
                                enabled = !actionInFlight,
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("Reject", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = onAccept,
                                enabled = !actionInFlight,
                                modifier = Modifier.weight(2f).height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5200))
                            ) {
                                Text("Accept Order", fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Countdown ring, floating above the sheet
                    CountdownRing(
                        secondsLeft = secondsLeft,
                        totalSeconds = offer.window_seconds,
                        modifier = Modifier.offset(y = (-40).dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownRing(secondsLeft: Int, totalSeconds: Int, modifier: Modifier = Modifier) {
    val fraction = if (totalSeconds > 0) secondsLeft.toFloat() / totalSeconds.toFloat() else 0f
    val urgent = secondsLeft <= 10
    val ringColor = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(80.dp)
            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = Color(0xFFF0EDED),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            String.format("00:%02d", secondsLeft.coerceAtLeast(0)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ringColor
        )
    }
}

@Composable
private fun RouteCard(offer: OrderOfferDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Pickup row
        RouteRow(
            dotColor = MaterialTheme.colorScheme.secondary,
            icon = null,
            label = "PICKUP" + (offer.distance_to_pickup_km?.let { " • %.1f km".format(it) } ?: ""),
            title = offer.pickup.name ?: "Restaurant",
            subtitle = offer.pickup.address,
            rating = offer.pickup.rating
        )

        Box(
            modifier = Modifier
                .padding(start = 15.dp)
                .width(2.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        // Dropoff row
        RouteRow(
            dotColor = null,
            icon = Icons.Default.LocationOn,
            label = "DROP-OFF" + (offer.distance_pickup_to_drop_km?.let { " • %.1f km".format(it) } ?: ""),
            title = offer.dropoff.address_line1 ?: offer.dropoff.label ?: "Customer address",
            subtitle = listOfNotNull(offer.dropoff.customer_name?.let { "Customer: $it" }, offer.dropoff.city).joinToString(" · ").ifBlank { null },
            rating = offer.customer_rating
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Total: ${offer.total_distance_km?.let { "%.1f km".format(it) } ?: "—"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    offer.est_delivery_minutes?.let { "~$it min" } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RouteRow(
    dotColor: Color?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    title: String,
    subtitle: String?,
    rating: Double?
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            } else if (dotColor != null) {
                Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(dotColor))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                rating?.let {
                    Spacer(Modifier.width(6.dp))
                    RatingChip(it)
                }
            }
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RatingChip(rating: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(
            "%.1f".format(rating),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

