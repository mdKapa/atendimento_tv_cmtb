package pt.cmtb.atendimentotv

import android.graphics.drawable.Drawable
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import jp.wasabeef.glide.transformations.BlurTransformation
import pt.cmtb.atendimentotv.databinding.ActivityMainBinding

/**
 * Palco de destaque com fundo dual e regra "primeiro a encostar":
 * compara a proporção da imagem com a do contentor e escolhe o modo de fundo.
 *
 * - Altura limitada (retrato no palco largo): documento encosta em cima/baixo → fundo papel nas laterais
 * - Largura limitada (paisagem): documento encosta à esquerda/direita → blur colorido em cima/baixo
 */
object DestaqueImageLoader {

  private const val PROBE_SIZE = 64
  private const val BACKDROP_W = 320
  private const val BACKDROP_H = 240
  private const val BLUR_RADIUS = 25
  private const val BLUR_SAMPLING = 4
  private const val FOREGROUND_W = 1920
  private const val FOREGROUND_H = 1080
  private const val CROSSFADE_MS = 200

  /** Imagem mais alta que o palco (em proporção) — encosta em cima/baixo. */
  private enum class FitDisposition { HEIGHT_LIMITED, WIDTH_LIMITED }

  private var probeTarget: CustomTarget<android.graphics.Bitmap>? = null

  fun load(activity: FragmentActivity, binding: ActivityMainBinding, imageUrl: String) {
    cancelPending(activity, binding)

    binding.imgDestaque.visibility = View.VISIBLE
    binding.viewDestaqueScrim.visibility = View.VISIBLE
    binding.imgDestaqueBackdrop.visibility = View.GONE
    binding.viewDestaquePaper.visibility = View.GONE

    binding.frameDestaque.post {
      if (!binding.frameDestaque.isAttachedToWindow) return@post
      startProbe(activity, binding, imageUrl)
    }
  }

  fun clear(activity: FragmentActivity, binding: ActivityMainBinding) {
    cancelPending(activity, binding)
    binding.imgDestaque.visibility = View.GONE
    binding.imgDestaqueBackdrop.visibility = View.GONE
    binding.viewDestaquePaper.visibility = View.GONE
    binding.viewDestaqueScrim.visibility = View.GONE
  }

  private fun startProbe(
    activity: FragmentActivity,
    binding: ActivityMainBinding,
    imageUrl: String
  ) {
    probeTarget = object : CustomTarget<android.graphics.Bitmap>(PROBE_SIZE, PROBE_SIZE) {
      override fun onResourceReady(
        resource: android.graphics.Bitmap,
        transition: Transition<in android.graphics.Bitmap>?
      ) {
        val disposition = resolveDisposition(
          imageW = resource.width,
          imageH = resource.height,
          containerW = binding.frameDestaque.width,
          containerH = binding.frameDestaque.height
        )
        applyBackdropMode(binding, disposition)
        loadForeground(activity, binding, imageUrl)
        loadBlurBackdrop(activity, binding, imageUrl, disposition)
      }

      override fun onLoadCleared(placeholder: Drawable?) = Unit

      override fun onLoadFailed(errorDrawable: Drawable?) {
        applyBackdropMode(binding, FitDisposition.WIDTH_LIMITED)
        loadForeground(activity, binding, imageUrl)
        loadBlurBackdrop(activity, binding, imageUrl, FitDisposition.WIDTH_LIMITED)
      }
    }

    Glide.with(activity)
      .asBitmap()
      .load(imageUrl)
      .override(PROBE_SIZE, PROBE_SIZE)
      .into(probeTarget!!)
  }

  private fun cancelPending(activity: FragmentActivity, binding: ActivityMainBinding) {
    probeTarget?.let { Glide.with(activity).clear(it) }
    probeTarget = null
    Glide.with(activity).clear(binding.imgDestaque)
    Glide.with(activity).clear(binding.imgDestaqueBackdrop)
  }

  /**
   * Primeiro eixo a encostar: se a imagem é mais "estreita" que o palco, a altura limita;
   * se é mais "larga", a largura limita.
   */
  private fun resolveDisposition(
    imageW: Int,
    imageH: Int,
    containerW: Int,
    containerH: Int
  ): FitDisposition {
    if (imageW <= 0 || imageH <= 0 || containerW <= 0 || containerH <= 0) {
      return FitDisposition.WIDTH_LIMITED
    }
    val imageAspect = imageW.toFloat() / imageH
    val containerAspect = containerW.toFloat() / containerH
    return if (imageAspect < containerAspect) {
      FitDisposition.HEIGHT_LIMITED
    } else {
      FitDisposition.WIDTH_LIMITED
    }
  }

  private fun applyBackdropMode(binding: ActivityMainBinding, disposition: FitDisposition) {
    when (disposition) {
      FitDisposition.HEIGHT_LIMITED -> {
        binding.imgDestaqueBackdrop.visibility = View.GONE
        binding.viewDestaquePaper.visibility = View.VISIBLE
        binding.viewDestaqueScrim.setBackgroundResource(R.color.destaque_scrim_portrait)
      }
      FitDisposition.WIDTH_LIMITED -> {
        binding.imgDestaqueBackdrop.visibility = View.VISIBLE
        binding.viewDestaquePaper.visibility = View.GONE
        binding.viewDestaqueScrim.setBackgroundResource(R.color.destaque_scrim_landscape)
      }
    }
  }

  private fun loadForeground(
    activity: FragmentActivity,
    binding: ActivityMainBinding,
    imageUrl: String
  ) {
    Glide.with(activity)
      .load(imageUrl)
      .override(FOREGROUND_W, FOREGROUND_H)
      .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_MS))
      .into(binding.imgDestaque)
  }

  private fun loadBlurBackdrop(
    activity: FragmentActivity,
    binding: ActivityMainBinding,
    imageUrl: String,
    disposition: FitDisposition
  ) {
    if (disposition == FitDisposition.HEIGHT_LIMITED) {
      Glide.with(activity).clear(binding.imgDestaqueBackdrop)
      return
    }

    Glide.with(activity)
      .load(imageUrl)
      .override(BACKDROP_W, BACKDROP_H)
      .transform(CenterCrop(), BlurTransformation(BLUR_RADIUS, BLUR_SAMPLING))
      .transition(DrawableTransitionOptions.withCrossFade(CROSSFADE_MS))
      .into(binding.imgDestaqueBackdrop)
  }
}
