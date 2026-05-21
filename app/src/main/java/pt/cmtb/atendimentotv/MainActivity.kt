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

    // =========================================================
    // FULLSCREEN
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

    // =========================================================
    // RECYCLERVIEW — grelha 3×2 categorias + 2 linhas documentos
    // =========================================================
    private fun setupRecyclerViews() {
        categoriasAdapter = CategoriasAdapter { categoria ->
            viewModel.selecionarCategoria(categoria.id)
        }
        binding.rvCategorias.apply {
            layoutManager = CategoriasAdapter.criarLayoutManager(this@MainActivity)
            adapter = categoriasAdapter
            itemAnimator = null
            addItemDecoration(GridSpacingDecoration(3, 4.dpToPx()))
        }

        documentosAdapter = DocumentosAdapter { documento ->
            viewModel.selecionarDocumento(documento)
        }
        binding.rvDocumentos.apply {
            layoutManager = DocumentosAdapter.criarLayoutManager(this@MainActivity)
            adapter = documentosAdapter
            itemAnimator = null
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    // =========================================================
    // EXOPLAYER — HLS com recuperação de BehindLiveWindow
    // =========================================================
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
                        android.util.Log.w("TV_PLAYER", "BehindLiveWindow — a voltar ao live edge")
                        exo.setMediaSource(buildMediaSource())
                        exo.prepare()
                        exo.seekToDefaultPosition()
                        exo.play()
                        return
                    }

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

    // =========================================================
    // RELÓGIO
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
            clockTimer.schedule(object : TimerTask() {
                override fun run() = tick()
            }, 1000L)
        }
        tick()
    }

    // =========================================================
    // BOTÕES DE NAVEGAÇÃO DE PÁGINA
    // =========================================================
    private fun setupBotoesPagina() {
        binding.btnPaginaAnterior.setOnClickListener { viewModel.recuarPagina() }
        binding.btnPaginaSeguinte.setOnClickListener { viewModel.avancarPagina() }
    }

    // =========================================================
    // OBSERVAR VIEWMODEL
    // =========================================================
    private fun observeViewModel() {

        // Categorias (já filtradas pelo ViewModel — sem documentos = ocultas)
        lifecycleScope.launch {
            viewModel.boardData.collectLatest { boardData ->
                boardData ?: return@collectLatest
                categoriasAdapter.submitList(boardData.categorias)
            }
        }

        // Categoria selecionada
        lifecycleScope.launch {
            viewModel.categoriaId.collectLatest { catId ->
                categoriasAdapter.setCategoriaAtiva(catId)
                val docs = viewModel.boardData.value?.documentos
                    ?.filter { it.categoriaId == catId }
                    ?: emptyList()
                documentosAdapter.submitList(docs)
            }
        }

        // Documento em destaque + navegação de páginas
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

        // IPMA — Risco de Incêndio
        // IPMA — HOJE
        lifecycleScope.launch {
            viewModel.ipmaHoje.collectLatest { estado ->
                // NOTA: Como usamos <include>, precisamos de usar a referência correta gerada pelo ViewBinding
                val card = binding.cardHoje

                // Forçamos a leitura dos títulos a partir do strings.xml
                card.tvTituloDia.text = getString(R.string.ipma_hoje)

                // Verificamos se o estado ainda não foi atualizado pela API. Se for o caso, usamos o "A carregar..." / "(N/D)" do strings.xml
                card.tvTempoDescricao.text = if (estado.descricaoTempo.contains("carregar") || estado.descricaoTempo == "--") getString(R.string.ipma_a_carregar) else estado.descricaoTempo
                card.tvTempoMinMax.text = estado.tempMaxMin

                card.tvRiscoNivel.text = if (estado.riscoNivel.contains("carregar") || estado.riscoNivel == "--") getString(R.string.ipma_a_carregar) else estado.riscoNivel

                // Prevenção de crash (fallback para cinza) caso a cor venha inválida
                val cor = try { android.graphics.Color.parseColor(estado.riscoCor) } catch(e: Exception) { android.graphics.Color.GRAY }
                card.tvRiscoNivel.setTextColor(cor)
                card.imgRiscoIncendio.setColorFilter(cor)
            }
        }

        // IPMA — AMANHÃ
        lifecycleScope.launch {
            viewModel.ipmaAmanha.collectLatest { estado ->
                val card = binding.cardAmanha

                card.tvTituloDia.text = getString(R.string.ipma_amanha)

                card.tvTempoDescricao.text = if (estado.descricaoTempo.contains("carregar") || estado.descricaoTempo == "--") getString(R.string.ipma_a_carregar) else estado.descricaoTempo
                card.tvTempoMinMax.text = estado.tempMaxMin

                card.tvRiscoNivel.text = if (estado.riscoNivel.contains("carregar") || estado.riscoNivel == "--") getString(R.string.ipma_a_carregar) else estado.riscoNivel

                val cor = try { android.graphics.Color.parseColor(estado.riscoCor) } catch(e: Exception) { android.graphics.Color.GRAY }
                card.tvRiscoNivel.setTextColor(cor)
                card.imgRiscoIncendio.setColorFilter(cor)
            }
        }

        // Erros de rede
        lifecycleScope.launch {
            viewModel.erro.collectLatest { erro ->
                erro ?: return@collectLatest
                binding.tvSemDocumento.text = erro
                binding.tvSemDocumento.visibility = View.VISIBLE
            }
        }
    }

    // =========================================================
    // DETEÇÃO DE TOQUE — reset do temporizador de inatividade
    // =========================================================
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            viewModel.resetInactivityTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    // =========================================================
    // RECONEXÃO DE REDE
    // =========================================================
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

    // =========================================================
    // FORÇAR 1080p
    // =========================================================
    private fun tryForce1080p() {
        val modes = display?.supportedModes
        val modo1080p = modes?.find { it.physicalWidth == 1920 && it.physicalHeight == 1080 }
        if (modo1080p != null) {
            val lp = window.attributes
            lp.preferredDisplayModeId = modo1080p.modeId
            window.attributes = lp
        }
    }

    // =========================================================
    // CICLO DE VIDA
    // =========================================================
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