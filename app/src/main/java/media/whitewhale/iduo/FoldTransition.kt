package media.whitewhale.iduo

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI

/**
 * Which screen Home last left because the phone folded or unfolded, and when. Android starts a
 * new Home window on the other screen, which reads this to know it arrived by a fold and should
 * come into focus rather than simply appear.
 */
internal object FoldHandoff {
    private var leftCover: Boolean? = null
    private var leftAt = 0L

    fun leave(cover: Boolean) {
        leftCover = cover
        leftAt = SystemClock.uptimeMillis()
        android.util.Log.i("DuoFold", "leave cover=$cover")
    }

    /** Whether this window on the [cover] or the inner screen replaces one on the other screen. */
    fun arrived(cover: Boolean): Boolean {
        val from = leftCover
        leftCover = null
        android.util.Log.i("DuoFold", "arrive cover=$cover from=$from age=${SystemClock.uptimeMillis() - leftAt}")
        return from != null && from != cover && SystemClock.uptimeMillis() - leftAt < ARRIVAL_WINDOW_MS
    }

    /** Longer than the slowest measured hand-over from one screen to the other. */
    private const val ARRIVAL_WINDOW_MS = 8_000L
}

/**
 * The hinge angle in degrees, 0 shut to 180 flat, or null until Android reports it. Samsung's
 * public hinge sensor only ever reports 0, 90 and 180, so on a Galaxy Fold this says shut, half
 * open or flat; other foldables and the emulator report every angle. Listens only while Home is
 * started.
 */
internal class HingeMonitor(context: Context) : DefaultLifecycleObserver, SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val hinge: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

    var angle by mutableStateOf<Float?>(null)
        private set

    override fun onStart(owner: LifecycleOwner) {
        hinge?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onStop(owner: LifecycleOwner) {
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        angle = event.values[0].coerceIn(0f, 180f)
        android.util.Log.i("DuoFold", "hinge $angle")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * How far Home is out of focus because the phone is folding or unfolding, 0 sharp to 1 fully
 * frosted. Half open is fully frosted on both screens. On the inner screen it clears as the
 * phone opens flat; on the [cover] it clears as the phone shuts. A window that [arrived] by a
 * fold starts frosted and clears once the hinge allows and Home is in front, not while the lock
 * screen still covers it.
 */
@Composable
internal fun rememberFoldFrost(cover: Boolean, arrived: Boolean, hinge: HingeMonitor): State<Float> {
    val frost = remember { Animatable(if (arrived) 1f else 0f) }
    val resumed = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val angle = hinge.angle
    val target = when {
        !resumed -> frost.value
        angle == null -> 0f
        cover -> (angle / HALF_OPEN_DEG).coerceIn(0f, 1f)
        else -> ((180f - angle) / (180f - HALF_OPEN_DEG)).coerceIn(0f, 1f)
    }
    LaunchedEffect(target) {
        // Clearing is the arrival and is given time to be seen; frosting answers the hand at once.
        if (target < frost.value) frost.animateTo(target, tween(CLEAR_MS, easing = LinearOutSlowInEasing))
        else frost.animateTo(target, tween(FROST_MS, easing = FastOutSlowInEasing))
    }
    return frost.asState()
}

/**
 * Draws Home as if the half of the phone that swings were turned toward the viewer and seen
 * through frosted glass: it widens away from the hinge and blurs more the further it lies from
 * it. On the inner screen that is the half opposite the dock, the cover's side; the cover
 * frosts from its hinge edge across to its free edge and hazes to white. Nothing happens below
 * Android 13, which has no runtime shaders.
 */
internal fun Modifier.foldFrost(frost: State<Float>, cover: Boolean): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) this else foldFrostEffect(frost, cover)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.foldFrostEffect(frost: State<Float>, cover: Boolean): Modifier =
    composed {
        val shader = remember { RuntimeShader(FOLD_FROST_SHADER) }
        val rotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0
        val pxPerDp = LocalDensity.current.density
        graphicsLayer {
            val amount = frost.value
            if (amount <= .001f || size.width <= 0f || size.height <= 0f) {
                renderEffect = null
                return@graphicsLayer
            }
            val (dx, dy) = freeEdgeDirection(cover, rotation)
            val across = if (dx != 0f) size.width else size.height
            val extent = if (cover) across else across / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            // The hinge runs through the middle of the inner screen and along the cover's edge.
            val hx = if (cover) cx - dx * across / 2f else cx
            val hy = if (cover) cy - dy * across / 2f else cy
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("hinge", hx, hy)
            shader.setFloatUniform("dir", dx, dy)
            shader.setFloatUniform("extent", extent)
            shader.setFloatUniform("turn", amount * MAX_TURN_DEG * PI.toFloat() / 180f)
            shader.setFloatUniform("eye", extent * EYE_DISTANCE)
            shader.setFloatUniform("blur", amount * (if (cover) COVER_BLUR_DP else INNER_BLUR_DP) * pxPerDp)
            shader.setFloatUniform("haze", amount * if (cover) COVER_HAZE else INNER_HAZE)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    }

/**
 * The way from the hinge to the swinging half's free edge on screen: to the left of the hinge on
 * the inner screen and to the right of it on the cover, in the screen's natural orientation.
 */
private fun freeEdgeDirection(cover: Boolean, rotation: Int): Pair<Float, Float> {
    val nx = if (cover) 1f else -1f
    return when (rotation) {
        Surface.ROTATION_90 -> 0f to -nx
        Surface.ROTATION_180 -> -nx to 0f
        Surface.ROTATION_270 -> 0f to nx
        else -> nx to 0f
    }
}

/**
 * Frosts Android's own wallpaper behind the swinging half, which the shader in [foldFrost] cannot
 * reach because Android draws it behind Home's window. Samsung's blur covers whole rectangles, so
 * the half is cut into strips that blur more the further they lie from the hinge. Only on
 * Samsung phones, and only for a wallpaper iDuo does not draw itself.
 */
@Composable
internal fun FoldWallpaperBlur(frost: State<Float>, cover: Boolean) {
    if (!SamsungBlur.available || HomeWallpaper.image != null) return
    // Each change of Samsung's blur redraws the window behind, so it moves in steps.
    val step by remember { androidx.compose.runtime.derivedStateOf {
        kotlin.math.round(frost.value * SAMSUNG_BLUR_STEPS).toInt() } }
    if (step == 0) return
    val rotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0
    val (dx, dy) = freeEdgeDirection(cover, rotation)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val across = if (dx != 0f) maxWidth else maxHeight
        val half = if (cover) across else across / 2
        val strip = half / WALLPAPER_STRIPS
        for (i in 0 until WALLPAPER_STRIPS) {
            // Strip i lies i strips out from the hinge toward the free edge.
            val near = if (cover) strip * i else across / 2 + strip * i
            val start = if ((if (dx != 0f) dx else dy) > 0f) near else across - near - strip
            val place = if (dx != 0f) Modifier.offset(x = start).width(strip).fillMaxHeight()
                else Modifier.offset(y = start).height(strip).fillMaxWidth()
            SamsungWallpaperBlur(FOLD_WALLPAPER_BLUR * step * (i + 1) / (SAMSUNG_BLUR_STEPS * WALLPAPER_STRIPS),
                0.dp, androidx.compose.ui.graphics.Color.Transparent, place)
        }
    }
}

private const val WALLPAPER_STRIPS = 4
private val FOLD_WALLPAPER_BLUR = 48.dp

/** Hinge angle that counts as half open, where both screens are fully frosted. */
private const val HALF_OPEN_DEG = 90f
private const val CLEAR_MS = 650
private const val FROST_MS = 300
/** How far the swinging half seems to turn toward the viewer when fully frosted. */
private const val MAX_TURN_DEG = 38f
/** Viewer's distance from the screen, in lengths of the swinging half. */
private const val EYE_DISTANCE = 2.6f
private const val INNER_BLUR_DP = 22f
private const val COVER_BLUR_DP = 16f
/** How far the cover's free edge whitens when fully frosted. */
private const val COVER_HAZE = .32f
/** A lighter haze on the inner screen, so the swinging half reads as glass over a bare wallpaper. */
private const val INNER_HAZE = .14f

/**
 * Per pixel: find the point of the turned half seen here, following a ray from a viewer in front
 * of the screen's middle, then average a disc of samples around it, wider the further it lies
 * from the hinge. Pixels on the other side of the hinge pass through untouched.
 */
private const val FOLD_FROST_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 hinge;
uniform float2 dir;
uniform float extent;
uniform float turn;
uniform float eye;
uniform float blur;
uniform float haze;

half4 main(float2 p) {
    float s = dot(p - hinge, dir);
    if (s <= 0.0) {
        return content.eval(p);
    }
    float2 side = float2(-dir.y, dir.x);
    float2 mid = size * 0.5;
    float sn = sin(turn);
    float cs = cos(turn);
    // Where along the turned half this pixel lands, and how much nearer the viewer that is.
    float d = s * eye / (eye * cs + s * sn);
    float scale = (eye - d * sn) / eye;
    float u = dot(p - mid, side) * scale;
    float2 at = mid + side * u + dir * (dot(hinge - mid, dir) + d);
    float t = clamp(d / extent, 0.0, 1.0);
    float r = blur * t * t * (3.0 - 2.0 * t);
    // Each pixel turns its disc by a different amount, so too few samples read as frosted grain
    // rather than as ghost copies of the picture.
    float spin = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453) * 6.28318;
    half4 sum = half4(0.0);
    for (int i = 0; i < 24; i++) {
        float k = float(i);
        float a = k * 2.39996 + spin;
        float rr = r * sqrt((k + 0.5) / 24.0);
        sum += content.eval(at + float2(cos(a), sin(a)) * rr);
    }
    half4 c = sum / 24.0;
    return mix(c, half4(1.0), half(haze * t));
}
"""
