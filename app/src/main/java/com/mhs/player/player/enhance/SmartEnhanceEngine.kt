package com.mhs.player.player.enhance

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect

/**
 * Custom Media3-compliant GPU Effect for real-time video enhancement.
 */
class SmartEnhanceGlEffect : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): androidx.media3.effect.GlShaderProgram {
        return SmartEnhanceGlShaderProgram(context, useHdr)
    }
}

/**
 * OpenGL ES 2.0 shader program running in real-time on the GPU frame texture.
 */
class SmartEnhanceGlShaderProgram(
    private val context: Context,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    // Standard vertex shader declaring standard attributes to avoid pipeline bind failures
    private val vertexShader = """
        attribute vec4 aFramePosition;
        attribute vec4 aTexSamplingCoordinate;
        varying vec2 vTexSamplingCoords;
        void main() {
            gl_Position = aFramePosition;
            vTexSamplingCoords = aTexSamplingCoordinate.xy;
        }
    """.trimIndent()

    // Safe passthrough fragment shader for emergency recovery & baseline validation
    private val passthroughFragmentShader = """
        precision mediump float;
        varying vec2 vTexSamplingCoords;
        uniform sampler2D uTexSampler;
        void main() {
            gl_FragColor = texture2D(uTexSampler, vTexSamplingCoords);
        }
    """.trimIndent()

    // High-performance intelligent video enhancement fragment shader
    private val fragmentShader = """
        precision mediump float;
        varying vec2 vTexSamplingCoords;
        uniform sampler2D uTexSampler;
        
        uniform float uSharpness;
        uniform float uContrast;
        uniform float uColorBoost;
        uniform float uNoiseReduction;
        uniform float uVideoWidth;
        uniform float uVideoHeight;

        void main() {
            vec2 texSize = vec2(uVideoWidth, uVideoHeight);
            if (texSize.x <= 0.0) texSize.x = 1920.0;
            if (texSize.y <= 0.0) texSize.y = 1080.0;
            vec2 step = 1.0 / texSize;
            
            vec4 color;
            if (uNoiseReduction > 0.0) {
                vec4 center = texture2D(uTexSampler, vTexSamplingCoords);
                vec4 left   = texture2D(uTexSampler, vTexSamplingCoords - vec2(step.x, 0.0));
                vec4 right  = texture2D(uTexSampler, vTexSamplingCoords + vec2(step.x, 0.0));
                vec4 top    = texture2D(uTexSampler, vTexSamplingCoords - vec2(0.0, step.y));
                vec4 bottom = texture2D(uTexSampler, vTexSamplingCoords + vec2(0.0, step.y));
                vec4 blurred = (center * 2.0 + left + right + top + bottom) / 6.0;
                color = mix(center, blurred, uNoiseReduction * 0.5);
            } else {
                color = texture2D(uTexSampler, vTexSamplingCoords);
            }
            
            if (uSharpness > 0.0) {
                vec4 center = color;
                vec4 left   = texture2D(uTexSampler, vTexSamplingCoords - vec2(step.x, 0.0));
                vec4 right  = texture2D(uTexSampler, vTexSamplingCoords + vec2(step.x, 0.0));
                vec4 top    = texture2D(uTexSampler, vTexSamplingCoords - vec2(0.0, step.y));
                vec4 bottom = texture2D(uTexSampler, vTexSamplingCoords + vec2(0.0, step.y));
                vec4 laplacian = center - (left + right + top + bottom) * 0.25;
                color.rgb = color.rgb + uSharpness * 0.75 * laplacian.rgb;
                color.rgb = clamp(color.rgb, 0.0, 1.0);
            }
            
            float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            if (uContrast > 0.0) {
                float contrastLuminance = luminance * luminance * (3.0 - 2.0 * luminance);
                if (luminance < 0.35) {
                    float shadowBoost = (1.0 - luminance / 0.35);
                    contrastLuminance = mix(contrastLuminance, contrastLuminance + 0.08 * shadowBoost, uContrast);
                }
                float finalLuminance = mix(luminance, contrastLuminance, uContrast * 0.85);
                color.rgb = color.rgb * (finalLuminance / max(luminance, 0.0001));
                color.rgb = clamp(color.rgb, 0.0, 1.0);
            }
            
            if (uColorBoost > 0.0) {
                float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                float r = color.r;
                float g = color.g;
                float b = color.b;
                bool isSkin = (r > 0.35 && g > 0.22 && b > 0.15 && r > g && r > b && (r - g) > 0.06);
                float activeBoost = uColorBoost * 0.65;
                if (isSkin) {
                    activeBoost *= 0.15;
                }
                color.rgb = mix(vec3(luma), color.rgb, 1.0 + activeBoost);
                color.rgb = clamp(color.rgb, 0.0, 1.0);
            }
            
            gl_FragColor = vec4(color.rgb, color.a);
        }
    """.trimIndent()

    private var glProgram: GlProgram? = null
    private var fallbackProgram: GlProgram? = null
    private var videoWidth = 1920
    private var videoHeight = 1080
    private var isUsingFallbackPassthrough = false

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        videoWidth = inputWidth
        videoHeight = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            // If Smart Enhance is disabled globally, force fallback rendering immediately
            if (!SmartEnhanceEngine.isEnabled) {
                isUsingFallbackPassthrough = true
            }

            if (isUsingFallbackPassthrough) {
                renderPassthroughInternal(inputTexId)
            } else {
                try {
                    renderFrameInternal(inputTexId, presentationTimeUs)
                } catch (e: Exception) {
                    Log.e("MHSPlayer-ShaderDebug", "Primary Smart Enhance shader execution failed. Falling back to passthrough.", e)
                    isUsingFallbackPassthrough = true
                    SmartEnhanceEngine.triggerErrorFallback()
                    renderPassthroughInternal(inputTexId)
                }
            }
        } catch (e: Exception) {
            Log.e("MHSPlayer-ShaderDebug", "Fatal error during drawFrame pipeline. Safe non-crashing recovery triggered.", e)
            SmartEnhanceEngine.triggerErrorFallback()
            // NEVER rethrow VideoFrameProcessingException to ensure playback stability.
        }
    }

    private fun renderFrameInternal(inputTexId: Int, presentationTimeUs: Long) {
        if (glProgram == null) {
            glProgram = GlProgram(vertexShader, fragmentShader)
            Log.d("MHSPlayer-ShaderDebug", "Full Smart Enhance shader compiled successfully!")
        }
        val program = glProgram ?: return
        drawWithProgram(program, inputTexId, isPassthrough = false)
    }

    private fun renderPassthroughInternal(inputTexId: Int) {
        if (fallbackProgram == null) {
            fallbackProgram = GlProgram(vertexShader, passthroughFragmentShader)
            Log.d("MHSPlayer-ShaderDebug", "Fallback Passthrough shader compiled successfully!")
        }
        val program = fallbackProgram ?: return
        drawWithProgram(program, inputTexId, isPassthrough = true)
    }

    private fun drawWithProgram(program: GlProgram, inputTexId: Int, isPassthrough: Boolean) {
        program.use()

        // Fetch dynamic status parameters for battery and thermal throttling state
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatterySaver = powerManager?.isPowerSaveMode ?: false
        val isThermalThrottling = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.getCurrentThermalStatus()?.let { it >= PowerManager.THERMAL_STATUS_MODERATE } ?: false
        } else {
            false
        }

        // Read adaptive weights from our SmartEnhanceEngine
        val activeContrast = if (isPassthrough) 0f else SmartEnhanceEngine.getActiveContrast(1f, isThermalThrottling, isBatterySaver)
        val activeSharpness = if (isPassthrough) 0f else SmartEnhanceEngine.getActiveSharpness(videoWidth, 1f, isThermalThrottling, isBatterySaver)
        val activeColorBoost = if (isPassthrough) 0f else SmartEnhanceEngine.getActiveColorBoost(1f, isThermalThrottling, isBatterySaver)
        val activeNoiseReduction = if (isPassthrough) 0f else SmartEnhanceEngine.getActiveNoiseReduction(videoWidth, 1f, isThermalThrottling, isBatterySaver)

        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        
        if (!isPassthrough) {
            program.setFloatUniform("uContrast", activeContrast)
            program.setFloatUniform("uSharpness", activeSharpness)
            program.setFloatUniform("uColorBoost", activeColorBoost)
            program.setFloatUniform("uNoiseReduction", activeNoiseReduction)
            program.setFloatUniform("uVideoWidth", videoWidth.toFloat())
            program.setFloatUniform("uVideoHeight", videoHeight.toFloat())
        }

        // Defensive requireNotNull bounds validation
        val framePosBuffer = GlUtil.getNormalizedCoordinateBounds()
        val texCoordBuffer = GlUtil.getTextureCoordinateBounds()
        
        requireNotNull(framePosBuffer) { "Media3 GL system returned null normalized coordinate bounds" }
        requireNotNull(texCoordBuffer) { "Media3 GL system returned null texture coordinate bounds" }

        program.setBufferAttribute(
            "aFramePosition",
            framePosBuffer,
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )

        program.setBufferAttribute(
            "aTexSamplingCoordinate",
            texCoordBuffer,
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )

        program.bindAttributesAndUniforms()
        android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
}

/**
 * Thread-safe global parameters controller for the AI Smart Enhance engine.
 */
object SmartEnhanceEngine {
    @Volatile var isEnabled: Boolean = true
    @Volatile var sharpness: Float = 0.4f
    @Volatile var contrast: Float = 0.3f
    @Volatile var colorBoost: Float = 0.3f
    @Volatile var noiseReduction: Float = 0.4f
    @Volatile var isAdaptive: Boolean = true

    // Emergency fallback notifier bound to the UI / PlayerController main loop
    @Volatile var onErrorOccurred: (() -> Unit)? = null
    @Volatile var fallbackTriggered: Boolean = false

    fun triggerErrorFallback() {
        if (!fallbackTriggered) {
            fallbackTriggered = true
            isEnabled = false // Disable immediately to bypass further frame operations
            onErrorOccurred?.invoke()
        }
    }

    fun isWeakGpu(context: Context): Boolean {
        val hw = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val isMali = hw.contains("mali") || board.contains("mali")
        val isPowerVR = hw.contains("powervr") || board.contains("powervr") || hw.contains("rogue")
        val isMediaTek = hw.contains("mt") || board.contains("mediatek")
        val isSpreadtrum = hw.contains("unisoc") || board.contains("spreadtrum") || hw.contains("sprd")
        val isAndroidTv = context.packageManager.hasSystemFeature("android.software.leanback")
        return isMali || isPowerVR || isMediaTek || isSpreadtrum || isAndroidTv
    }

    fun setParams(
        enabled: Boolean,
        sharp: Float,
        cont: Float,
        color: Float,
        noise: Float,
        adaptive: Boolean
    ) {
        isEnabled = enabled
        if (enabled) {
            fallbackTriggered = false
        }
        sharpness = sharp
        contrast = cont
        colorBoost = color
        noiseReduction = noise
        isAdaptive = adaptive
        Log.d("MHSPlayer-SmartEnhance", "setParams: enabled=$enabled, sharp=$sharp, cont=$cont, color=$color, noise=$noise, adaptive=$adaptive")
    }

    fun getActiveSharpness(videoWidth: Int, batteryPct: Float, isThermalThrottling: Boolean, isBatterySaver: Boolean): Float {
        if (!isEnabled) return 0f
        var value = sharpness
        if (isBatterySaver || isThermalThrottling) value *= 0.3f
        return value
    }

    fun getActiveContrast(batteryPct: Float, isThermalThrottling: Boolean, isBatterySaver: Boolean): Float {
        if (!isEnabled) return 0f
        var value = contrast
        if (isBatterySaver || isThermalThrottling) value *= 0.3f
        return value
    }

    fun getActiveColorBoost(batteryPct: Float, isThermalThrottling: Boolean, isBatterySaver: Boolean): Float {
        if (!isEnabled) return 0f
        var value = colorBoost
        if (isBatterySaver || isThermalThrottling) value *= 0.3f
        return value
    }

    fun getActiveNoiseReduction(videoWidth: Int, batteryPct: Float, isThermalThrottling: Boolean, isBatterySaver: Boolean): Float {
        if (!isEnabled) return 0f
        var value = noiseReduction
        if (isBatterySaver || isThermalThrottling) value *= 0.3f
        return value
    }
}
