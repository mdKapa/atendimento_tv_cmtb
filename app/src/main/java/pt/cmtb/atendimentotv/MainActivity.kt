package pt.cmtb.atendimentotv

import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pt.cmtb.atendimentotv.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

// @OptIn resolve todos os avisos "UnstableApi" do Media3 de uma vez
// É a forma correta de usar HlsMediaSource e DefaultHttpDataSource
@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var player: ExoPlayer? = null
    private lateinit var categoriasAdapter: CategoriasAdapter
    private lateinit var documentosAdapter: DocumentosAdapter
    private val clockTimer = Timer()
    private val clockHandler = Handler(Looper.getMainLooper())

    private val streamUrl =
        "https://streaming-live.rtp.pt/liverepeater/rtpnHD.smil/playlist.m3u8"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tryForce1080p()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Oculta a UI do sistema SÓ DEPOIS da interface estar desenhada
        hideSystemUi()

        setupRecyclerViews()
        setupExoPlayer()
        setupClock()
        setupBotoesPagina()
        observeViewModel()
        setupNetworkCallback()
    }

    // =========================================================
    // FULLSCREEN — substitui o systemUiVisibility deprecated
    // =========================================================
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun setupRecyclerViews() {
        categoriasAdapter = CategoriasAdapter { categoria ->
            viewModel.selecionarCategoria(categoria.id)
        }
        binding.rvCategorias.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = categoriasAdapter
            itemAnimator = null
        }

        documentosAdapter = DocumentosAdapter { documento ->
            viewModel.selecionarDocumento(documento)
        }
        binding.rvDocumentos.apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = documentosAdapter
            itemAnimator = null
        }
    }

    // =========================================================
    // EXOPLAYER — @OptIn na classe trata todos os UnstableApi
    // =========================================================
    private fun setupExoPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.volume = 0f

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 11)")
                .setConnectTimeoutMs(5000)
                .setReadTimeoutMs(10000)
                .setAllowCrossProtocolRedirects(true)

            val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(streamUrl))

            exo.setMediaSource(mediaSource)
            exo.prepare()
            exo.playWhenReady = true
            exo.repeatMode = Player.REPEAT_MODE_ALL

            var retryCount = 0
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (retryCount < 5) {
                        retryCount++
                        val delayMs = retryCount * 3000L
                        Handler(Looper.getMainLooper()).postDelayed({
                            exo.prepare()
                            exo.play()
                        }, delayMs)
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) retryCount = 0
                }
            })
        }
    }

    // =========================================================
    // RELÓGIO — nome ASCII, usa schedule em vez de scheduleAtFixedRate
    // =========================================================
    private fun setupClock() {
        val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "PT"))
        val formatoData = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("pt", "PT"))

        fun tick() {
            val agora = Date()
            val hora = formatoHora.format(agora)
            var data = formatoData.format(agora)
            data = data.replaceFirstChar { it.uppercase() }
            clockHandler.post {
                binding.tvHora.text = hora
                binding.tvData.text = data
            }
            // schedule em vez de scheduleAtFixedRate — evita execuções em burst
            clockTimer.schedule(object : TimerTask() {
                override fun run() = tick()
            }, 1000L)
        }
        tick()
    }

    private fun setupBotoesPagina() {
        binding.btnPaginaAnterior.setOnClickListener { viewModel.recuarPagina() }
        binding.btnPaginaSeguinte.setOnClickListener { viewModel.avancarPagina() }
    }

    private fun observeViewModel() {

        lifecycleScope.launch {
            viewModel.boardData.collectLatest { boardData ->
                boardData ?: return@collectLatest
                categoriasAdapter.submitList(boardData.categorias)
            }
        }

        lifecycleScope.launch {
            viewModel.categoriaId.collectLatest { catId ->
                categoriasAdapter.setCategoriaAtiva(catId)
                val docs = viewModel.boardData.value?.documentos
                    ?.filter { it.categoriaId == catId }
                    ?: emptyList()
                documentosAdapter.submitList(docs)
            }
        }

        lifecycleScope.launch {
            viewModel.destaque.collectLatest { estado ->
                val doc = estado.documento
                val pagina = estado.paginaAtual

                documentosAdapter.setDocumentoAtivo(doc?.id)

                if (doc == null || doc.paginas.isEmpty()) {
                    binding.tvSemDocumento.visibility = View.VISIBLE
                    binding.imgDestaque.visibility = View.GONE
                    binding.tvPaginaIndicador.visibility = View.GONE
                    binding.btnPaginaAnterior.visibility = View.GONE
                    binding.btnPaginaSeguinte.visibility = View.GONE
                    return@collectLatest
                }

                binding.tvSemDocumento.visibility = View.GONE
                binding.imgDestaque.visibility = View.VISIBLE

                Glide.with(this@MainActivity)
                    .load(doc.paginas[pagina])
                    .override(1920, 1080)
                    .into(binding.imgDestaque)

                if (doc.paginas.size > 1) {
                    binding.tvPaginaIndicador.visibility = View.VISIBLE
                    // String em res/values/strings.xml evita o aviso de tradução
                    binding.tvPaginaIndicador.text =
                        getString(R.string.indicador_pagina, pagina + 1, doc.paginas.size)
                } else {
                    binding.tvPaginaIndicador.visibility = View.GONE
                }

                binding.btnPaginaAnterior.visibility =
                    if (pagina > 0) View.VISIBLE else View.GONE
                binding.btnPaginaSeguinte.visibility =
                    if (pagina < doc.paginas.size - 1) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.erro.collectLatest { erro ->
                erro ?: return@collectLatest
                binding.tvSemDocumento.text = erro
                binding.tvSemDocumento.visibility = View.VISIBLE
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            viewModel.resetInactivityTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupNetworkCallback() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val p = player ?: return@postDelayed
                    if (!p.isPlaying) {
                        p.prepare()
                        p.play()
                    }
                }, 2000)
            }
        })
    }

    // =========================================================
    // FORÇAR 1080p — sem deprecated, minSdk 26 garante M+
    // =========================================================
    private fun tryForce1080p() {
        val modes = windowManager.currentWindowMetrics.let {
            // Tentativa via Display.Mode (API 23+, sempre disponível com minSdk 26)
            display?.supportedModes
        }
        val modo1080p = modes?.find { it.physicalWidth == 1920 && it.physicalHeight == 1080 }
        if (modo1080p != null) {
            val lp = window.attributes
            lp.preferredDisplayModeId = modo1080p.modeId
            window.attributes = lp
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        clockTimer.cancel()
    }
}