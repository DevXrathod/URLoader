package com.dev.urloader.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.dev.urloader.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * High-performance animated multi-layered background featuring:
 * 1. Layer 1 (Base): True OLED Pitch Black (#000000)
 * 2. Layer 2 (Mid): Focused, highly-saturated floating gradient glow orbs
 * 3. Layer 3 (Depth): Multi-pass Black Depth layer (radial vignette + top/bottom dark depth scrims)
 *
 * Recreates the Dribbble/Gemini dark contrasty aesthetic:
 * intense glowing neon aurora pools emerging with sharp contrast from deep pitch-black space.
 */
class BlurMeshBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class GlowOrb(
        val baseRatioX: Float,
        val baseRatioY: Float,
        val radiusRatio: Float,
        val colorHex: Int,
        val alpha: Int,
        val phase: Float,
        val speedX: Float,
        val speedY: Float,
        val pulseSpeed: Float = 1.0f,
        val pulseAmplitude: Float = 0.15f,
        var calculatedRadius: Float = 0f,
        val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    )

    private val topScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bottomScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Three tight, concentrated, high-contrast glow pools
    private val orbs = listOf(
        // Glow 1: Vibrant Cobalt / Electric Blue (Left-Center focal glow)
        GlowOrb(
            baseRatioX = 0.30f,
            baseRatioY = 0.38f,
            radiusRatio = 0.44f,
            colorHex = ContextCompat.getColor(context, R.color.mesh_blue_primary),
            alpha = 245,
            phase = 0.0f,
            speedX = 1.0f,
            speedY = 1.0f,
            pulseSpeed = 1.0f,
            pulseAmplitude = 0.15f
        ),
        // Glow 2: Intense Electric Cyan (Right-Center neon accent)
        GlowOrb(
            baseRatioX = 0.72f,
            baseRatioY = 0.44f,
            radiusRatio = 0.38f,
            colorHex = ContextCompat.getColor(context, R.color.mesh_cyan_neon),
            alpha = 235,
            phase = 1.5707964f, // PI / 2
            speedX = 1.0f,
            speedY = 2.0f,
            pulseSpeed = 1.0f,
            pulseAmplitude = 0.16f
        ),
        // Glow 3: Deep Cosmic Violet (Lower-Center depth body)
        GlowOrb(
            baseRatioX = 0.48f,
            baseRatioY = 0.58f,
            radiusRatio = 0.42f,
            colorHex = ContextCompat.getColor(context, R.color.mesh_violet_neon),
            alpha = 215,
            phase = 3.1415927f, // PI
            speedX = 2.0f,
            speedY = 1.0f,
            pulseSpeed = 1.0f,
            pulseAmplitude = 0.14f
        )
    )

    var enableDepthScrims: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (width > 0 && height > 0) {
                    rebuildShaders(width, height)
                    invalidate()
                }
            }
        }

    private var animator: ValueAnimator? = null
    private var animationProgress = 0f

    companion object {
        private const val ANIMATION_DURATION_MS = 7000L
        private const val BLUR_RADIUS = 50f
        private const val TWO_PI = Math.PI * 2.0
    }

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BlurMeshBackgroundView, defStyleAttr, 0)
            try {
                enableDepthScrims = typedArray.getBoolean(R.styleable.BlurMeshBackgroundView_enableDepthScrims, true)
            } finally {
                typedArray.recycle()
            }
        }

        // Enable hardware Gaussian blur on Android 12+ (SDK 31-36)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val blurEffect = RenderEffect.createBlurEffect(
                    BLUR_RADIUS,
                    BLUR_RADIUS,
                    Shader.TileMode.CLAMP
                )
                setRenderEffect(blurEffect)
            } catch (_: Exception) {
                // Graceful fallback for devices that don't support RenderEffect
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val w = if (widthMode == MeasureSpec.UNSPECIFIED || widthMode == MeasureSpec.AT_MOST) {
            suggestedMinimumWidth
        } else {
            MeasureSpec.getSize(widthMeasureSpec)
        }

        val h = if (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST) {
            suggestedMinimumHeight
        } else {
            MeasureSpec.getSize(heightMeasureSpec)
        }

        setMeasuredDimension(
            resolveSize(w, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders(w, h)
    }

    private fun rebuildShaders(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val baseDim = max(w, h).toFloat()

        // 1. Rebuild focused, high-contrast glow orb shaders with steep falloff
        for (orb in orbs) {
            val radius = baseDim * orb.radiusRatio
            orb.calculatedRadius = radius

            val r = Color.red(orb.colorHex)
            val g = Color.green(orb.colorHex)
            val b = Color.blue(orb.colorHex)

            // Steep, punchy falloff: rich saturated core dropping quickly to transparent
            val c0 = Color.argb(orb.alpha, r, g, b)
            val c1 = Color.argb((orb.alpha * 0.55f).toInt(), r, g, b)
            val c2 = Color.argb((orb.alpha * 0.12f).toInt(), r, g, b)
            val c3 = Color.TRANSPARENT

            orb.paint.shader = RadialGradient(
                0f,
                0f,
                radius,
                intArrayOf(c0, c1, c2, c3),
                floatArrayOf(0.0f, 0.25f, 0.58f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }

        if (enableDepthScrims) {
            // 2. Black Depth Pass: Top Header Scrim
            val topDepthH = h * 0.28f
            topScrimPaint.shader = LinearGradient(
                0f, 0f, 0f, topDepthH,
                intArrayOf(
                    Color.BLACK,
                    Color.BLACK,
                    Color.argb(180, 0, 0, 0),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.0f, 0.40f, 0.75f, 1.0f),
                Shader.TileMode.CLAMP
            )

            // 3. Black Depth Pass: Bottom Footer Scrim
            val bottomDepthY = h * 0.58f
            bottomScrimPaint.shader = LinearGradient(
                0f, bottomDepthY, 0f, h.toFloat(),
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(180, 0, 0, 0),
                    Color.BLACK,
                    Color.BLACK
                ),
                floatArrayOf(0.0f, 0.25f, 0.65f, 1.0f),
                Shader.TileMode.CLAMP
            )

            // 4. Black Depth Pass: Screen-edge Radial Vignette (keeps perimeter in pitch black)
            val maxDim = max(w, h).toFloat()
            vignettePaint.shader = RadialGradient(
                w * 0.5f,
                h * 0.45f,
                maxDim * 0.60f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.argb(150, 0, 0, 0),
                    Color.argb(240, 0, 0, 0),
                    Color.BLACK
                ),
                floatArrayOf(0.0f, 0.35f, 0.60f, 0.85f, 1.0f),
                Shader.TileMode.CLAMP
            )
        } else {
            // Card Mode: Gentle corner shadow vignette, preserving glow across card center & actions
            val maxDim = max(w, h).toFloat()
            vignettePaint.shader = RadialGradient(
                w * 0.5f,
                h * 0.50f,
                maxDim * 0.85f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.argb(100, 0, 0, 0),
                    Color.argb(200, 0, 0, 0),
                    Color.BLACK
                ),
                floatArrayOf(0.0f, 0.45f, 0.72f, 0.90f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Layer 1: True OLED Pitch Black Base
        canvas.drawColor(Color.BLACK)

        // Layer 2: Floating Concentrated Gradient Glow Orbs
        val driftRangeX = w * 0.14f
        val driftRangeY = h * 0.12f
        val angle = (animationProgress * TWO_PI).toFloat()

        for (orb in orbs) {
            val baseX = w * orb.baseRatioX
            val baseY = h * orb.baseRatioY

            val offsetX = sin(angle * orb.speedX + orb.phase) * driftRangeX
            val offsetY = cos(angle * orb.speedY + orb.phase) * driftRangeY
            val scale = 1.0f + orb.pulseAmplitude * sin(angle * orb.pulseSpeed + orb.phase * 1.5f)

            canvas.save()
            canvas.translate(baseX + offsetX, baseY + offsetY)
            canvas.scale(scale, scale)
            canvas.drawCircle(0f, 0f, orb.calculatedRadius, orb.paint)
            canvas.restore()
        }

        // Layer 3: Multi-layer Black Depth Scrims & Vignette (carves out true black contrast)
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
        if (enableDepthScrims) {
            canvas.drawRect(0f, 0f, w, h * 0.28f, topScrimPaint)
            canvas.drawRect(0f, h * 0.58f, w, h, bottomScrimPaint)
        }
    }

    fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                animationProgress = anim.animatedFraction
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }
}

