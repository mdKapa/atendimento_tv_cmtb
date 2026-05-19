package pt.cmtb.atendimentotv

import android.graphics.Color
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
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pt.cmtb.atendimentotv.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

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

        hideSystemUi()
        setupRecyclerViews()
        setupExoPlayer()
        setupClock()
        setupBotoesPagina()
        observeViewModel()
        setupNetworkCallback()
    }

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
        // --- Tarefa 3: Categorias em grelha 2 colunas ---
        categoriasAdapter = CategoriasAdapter { categoria ->
            viewModel.selecionarCategoria(categoria.id)
        }
        binding.rvCategorias.apply {
            // GridLayoutManager com 2 colunas, orientação vertical (padrão)
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = categoriasAdapter
            itemAnimator = null
        }

        // --- Tarefa 4: Documentos em grelha 2 linhas com scroll horizontal ---
        documentosAdapter = DocumentosAdapter { documento ->
            viewModel.selecionarDocumento(documento)
        }
        binding.rvDocumentos.apply {
            // GridLayoutManager: 2 spans na vertical + crescimento HORIZONTAL
            // = 2 linhas fixas, scroll para a direita quando há mais de 8 docs
            layoutManager = DocumentosAdapter.criarLayoutManager(this@MainActivity)
            adapter = documentosAdapter
            itemAnimator = null
        }
    }

    private fun buildMediaSource(): HlsMediaSource {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11)")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
        return HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl))
    }

    private fun setupExoPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.volume = 0f
            exo.playWhenReady = true
            exo.repeatMode = Player.REPEAT_MODE_ALL

            exo.setMediaSource(buildMediaSource())
            exo.prepare()

            var retryCount = 0

            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val isBehindWindow = error.cause is
                            androidx.media3.exoplayer.source.BehindLiveWindowException

                    if (isBehindWindow) {
                        // BehindLiveWindowException: o player ficou para trás da
                        // janela deslizante da stream live.
                        // Solução: recriar a source e saltar para o live edge.
                        android.util.Log.w("TV_PLAYER", "BehindLiveWindow — a voltar ao live edge")
                        exo.setMediaSource(buildMediaSource())
                        exo.prepare()
                        // seekToDefaultPosition força o live edge imediatamente
                        exo.seekToDefaultPosition()
                        exo.play()
                        return
                    }

                    // Outros erros: backoff exponencial até 5 tentativas
                    if (retryCount < 5) {
                        retryCount++
                        val delayMs = minOf(retryCount * 4000L, 20_000L)
                        android.util.Log.w("TV_PLAYER", "Erro #$retryCount — retry em ${delayMs}ms")
                        Handler(Looper.getMainLooper()).postDelayed({
                            exo.setMediaSource(buildMediaSource())
                            exo.prepare()
                            exo.play()
                        }, delayMs)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        retryCount = 0
                        android.util.Log.i("TV_PLAYER", "A reproduzir")
                    }
                }
            })
        }
    }

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

        // --- Dados: categorias ---
        lifecycleScope.launch {
            viewModel.boardData.collectLatest { boardData ->
                boardData ?: return@collectLatest
                categoriasAdapter.submitList(boardData.categorias)
            }
        }

        // --- Categoria selecionada ---
        lifecycleScope.launch {
            viewModel.categoriaId.collectLatest { catId ->
                categoriasAdapter.setCategoriaAtiva(catId)
                val docs = viewModel.boardData.value?.documentos
                    ?.filter { it.categoriaId == catId }
                    ?: emptyList()
                documentosAdapter.submitList(docs)
            }
        }

        // --- Documento em destaque ---
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

        // --- Tarefa 5: Risco de Incêndio IPMA ---
        lifecycleScope.launch {
            viewModel.riscoIncendio.collectLatest { estado ->
                binding.tvRiscoNivel.text = estado.nivel
                binding.tvRiscoLocal.text = estado.local

                // Pintar o texto e ícone com a cor do nível de risco
                val cor = Color.parseColor(estado.cor)
                binding.tvRiscoNivel.setTextColor(cor)
                binding.imgRiscoIncendio.setColorFilter(cor)
            }
        }

        // --- Erros ---
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
                    if (!p.isPlaying) { p.prepare(); p.play() }
                }, 2000)
            }
        })
    }

    private fun tryForce1080p() {
        val modes = display?.supportedModes
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