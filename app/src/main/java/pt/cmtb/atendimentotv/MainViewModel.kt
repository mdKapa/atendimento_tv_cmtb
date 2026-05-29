package pt.cmtb.atendimentotv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppMode { MANUAL, AUTOMATICO }

/** Conteúdo do palco de destaque: documentos (interação) ou página de eventos (inatividade). */
enum class DestaqueModo { DOCUMENTO, VISTA_EVENTOS }

data class DestaqueUiState(
    val documento: DocumentoModel? = null,
    val paginaAtual: Int = 0
)

class MainViewModel : ViewModel() {

    // --- Dados da API interna ---
    private val _boardData = MutableStateFlow<BoardData?>(null)
    val boardData: StateFlow<BoardData?> = _boardData.asStateFlow()

    /** Categorias da API (máx. 3) + «Eventos» fixa = 4 botões na grelha. */
    private val _categoriasUi = MutableStateFlow(listOf(CategoriaEstatica.EVENTOS))
    val categoriasUi: StateFlow<List<CategoriaModel>> = _categoriasUi.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _erro = MutableStateFlow<String?>(null)
    val erro: StateFlow<String?> = _erro.asStateFlow()

    // --- Categoria selecionada ---
    private val _categoriaId = MutableStateFlow<String?>(null)
    val categoriaId: StateFlow<String?> = _categoriaId.asStateFlow()

    // --- Documento em destaque ---
    private val _destaque = MutableStateFlow(DestaqueUiState())
    val destaque: StateFlow<DestaqueUiState> = _destaque.asStateFlow()

    private val _destaqueModo = MutableStateFlow(DestaqueModo.VISTA_EVENTOS)
    val destaqueModo: StateFlow<DestaqueModo> = _destaqueModo.asStateFlow()

    /** Incrementa para forçar recarga do site de eventos no URL inicial. */
    private val _webResetTrigger = MutableStateFlow(0L)
    val webResetTrigger: StateFlow<Long> = _webResetTrigger.asStateFlow()

    // --- Modo da app ---
    private val _appMode = MutableStateFlow(AppMode.AUTOMATICO)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    // --- ESTADOS IPMA (HOJE e AMANHÃ) ---
    // Mantemos os títulos limpos para o UI assumir via strings.xml
    private val _ipmaHoje = MutableStateFlow(IpmaWidgetState("Hoje"))
    val ipmaHoje: StateFlow<IpmaWidgetState> = _ipmaHoje.asStateFlow()

    private val _ipmaAmanha = MutableStateFlow(IpmaWidgetState("Amanhã"))
    val ipmaAmanha: StateFlow<IpmaWidgetState> = _ipmaAmanha.asStateFlow()

    // --- Timers e jobs ---
    private var inactivityJob: Job? = null
    private val inactivityDuration = 60_000L

    private var slideshowJob: Job? = null
    private var slideshowIndex = 0
    private val slideshowInterval = 45_000L

    private var lastVersion = -1
    private var pollingJob: Job? = null
    private var ipmaJob: Job? = null

    // --- Constantes do IPMA ---
    private val DICO_TERRAS_DE_BOURO = "0310" // Código DICO para o Risco de Incêndio
    private val GLOBAL_ID_BRAGA = 1030300     // Código Global (Distrito) para o Tempo

    init {
        fetchBoardData()
        startVersionPolling()
        startIpmaPolling()
        enterIdleMode()
    }

    // =========================================================
    // FUNÇÕES PÚBLICAS
    // =========================================================

    /** Reinicia o temporizador sem sair da vista de eventos (toque genérico no ecrã). */
    fun resetInactivityTimer() {
        startInactivityTimer()
    }

    private fun enterManualMode() {
        if (_appMode.value != AppMode.MANUAL) {
            _appMode.value = AppMode.MANUAL
            stopSlideshow()
        }
        _destaqueModo.value = DestaqueModo.DOCUMENTO
        startInactivityTimer()
    }

    private fun enterIdleMode() {
        _appMode.value = AppMode.AUTOMATICO
        _destaqueModo.value = DestaqueModo.VISTA_EVENTOS
        _categoriaId.value = CategoriaEstatica.EVENTOS_ID
        _destaque.value = DestaqueUiState()
        stopSlideshow()
        _webResetTrigger.value++
        startInactivityTimer()
    }

    fun selecionarCategoria(categoriaId: String) {
        if (categoriaId == CategoriaEstatica.EVENTOS_ID) {
            selecionarVistaEventos()
            return
        }
        enterManualMode()
        _categoriaId.value = categoriaId
        val docs = _boardData.value?.documentos
            ?.filter { it.categoriaId == categoriaId }
            ?: emptyList()
        _destaque.value = DestaqueUiState(
            documento = docs.firstOrNull(),
            paginaAtual = 0
        )
    }

    /** Categoria estática «Eventos» — abre o website no palco (sem documentos). */
    fun selecionarVistaEventos() {
        if (_appMode.value != AppMode.MANUAL) {
            _appMode.value = AppMode.MANUAL
            stopSlideshow()
        }
        _destaqueModo.value = DestaqueModo.VISTA_EVENTOS
        _categoriaId.value = CategoriaEstatica.EVENTOS_ID
        _destaque.value = DestaqueUiState()
        startInactivityTimer()
    }

    fun selecionarDocumento(doc: DocumentoModel) {
        enterManualMode()
        _categoriaId.value = doc.categoriaId
        _destaque.value = DestaqueUiState(documento = doc, paginaAtual = 0)
    }

    fun avancarPagina() {
        enterManualMode()
        val estado = _destaque.value
        val maxPaginas = estado.documento?.paginas?.size ?: 1
        if (estado.paginaAtual < maxPaginas - 1) {
            _destaque.value = estado.copy(paginaAtual = estado.paginaAtual + 1)
        }
    }

    fun recuarPagina() {
        enterManualMode()
        val estado = _destaque.value
        if (estado.paginaAtual > 0) {
            _destaque.value = estado.copy(paginaAtual = estado.paginaAtual - 1)
        }
    }

    private fun buildCategoriasUi(apiCategorias: List<CategoriaModel>): List<CategoriaModel> =
        apiCategorias.take(3) + CategoriaEstatica.EVENTOS

    // =========================================================
    // LÓGICA DO QUADRO PRINCIPAL (API INTERNA)
    // =========================================================

    private fun fetchBoardData() {
        viewModelScope.launch {
            _isLoading.value = true
            _erro.value = null
            try {
                val rawData = ApiClient.service.fetchBoardData()
                val idsComDocumentos = rawData.documentos.map { it.categoriaId }.toSet()

                val categoriasFiltradas = rawData.categorias
                    .filter { categoria -> categoria.id in idsComDocumentos }
                    .filter { it.id != CategoriaEstatica.EVENTOS_ID }

                val dataFiltrada = rawData.copy(categorias = categoriasFiltradas)
                _boardData.value = dataFiltrada
                _categoriasUi.value = buildCategoriasUi(categoriasFiltradas)

            } catch (e: Exception) {
                _erro.value = "Erro ao carregar dados: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startVersionPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val v = ApiClient.service.fetchBoardVersion().version
                    if (lastVersion != -1 && v != lastVersion) fetchBoardData()
                    lastVersion = v
                } catch (_: Exception) {}
                delay(5 * 60 * 1000L)
            }
        }
    }

    // =========================================================
    // IPMA: Coroutines Paralelas (Risco PIR + Estado do Tempo)
    // =========================================================

    private fun fetchIpmaDataConcorrente() {
        viewModelScope.launch {
            try {
                // 1. Disparamos os 4 pedidos de rede SIMULTANEAMENTE (Performance Máxima)
                val reqRcmHoje = async { IpmaClient.service.fetchRcm(0) }
                val reqRcmAmanha = async { IpmaClient.service.fetchRcm(1) }
                val reqTempoHoje = async { IpmaClient.service.fetchPrevisaoTempo(0) }
                val reqTempoAmanha = async { IpmaClient.service.fetchPrevisaoTempo(1) }

                // 2. Aguardamos que TODOS os 4 terminem
                val resRcmHoje = reqRcmHoje.await()
                val resRcmAmanha = reqRcmAmanha.await()
                val resTempoHoje = reqTempoHoje.await()
                val resTempoAmanha = reqTempoAmanha.await()

                // 3. Processar Dados de HOJE
                val rcmHoje = resRcmHoje.local[DICO_TERRAS_DE_BOURO]?.data?.rcm ?: 0
                val tempoHoje = resTempoHoje.data.find { it.globalIdLocal == GLOBAL_ID_BRAGA }

                if (tempoHoje != null) {
                    _ipmaHoje.value = _ipmaHoje.value.copy(
                        idTipoTempo = tempoHoje.idWeatherType,
                        descricaoTempo = mapearIdTempo(tempoHoje.idWeatherType),
                        tempMaxMin = "${tempoHoje.tMin}º | ${tempoHoje.tMax}º",
                        riscoNivel = IpmaClient.nivelTexto(rcmHoje),
                        classRisco = rcmHoje,
                        riscoCor = IpmaClient.nivelCor(rcmHoje)
                    )
                }

                // 4. Processar Dados de AMANHÃ
                val rcmAmanha = resRcmAmanha.local[DICO_TERRAS_DE_BOURO]?.data?.rcm ?: 0
                val tempoAmanha = resTempoAmanha.data.find { it.globalIdLocal == GLOBAL_ID_BRAGA }

                if (tempoAmanha != null) {
                    _ipmaAmanha.value = _ipmaAmanha.value.copy(
                        idTipoTempo = tempoAmanha.idWeatherType,
                        descricaoTempo = mapearIdTempo(tempoAmanha.idWeatherType),
                        tempMaxMin = "${tempoAmanha.tMin}º | ${tempoAmanha.tMax}º",
                        riscoNivel = IpmaClient.nivelTexto(rcmAmanha),
                        classRisco = rcmAmanha,
                        riscoCor = IpmaClient.nivelCor(rcmAmanha)
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("IPMA", "Erro ao carregar dados do IPMA: ${e.message}")
            }
        }
    }

    private fun startIpmaPolling() {
        ipmaJob?.cancel()
        ipmaJob = viewModelScope.launch {
            while (isActive) {
                fetchIpmaDataConcorrente()
                delay(60 * 60 * 1000L) // Atualiza de 1 em 1 hora
            }
        }
    }

    /**
     * Mapeia o código do estado do tempo da API do IPMA para String legível.
     * Textos abreviados para garantir que não quebram o layout no ecrã da TV.
     */
    private fun mapearIdTempo(id: Int): String {
        return when (id) {
            1 -> "Céu Limpo"
            2, 3 -> "Pouco Nublado"
            4, 5, 24, 25, 26, 27 -> "Muito Nublado"
            6, 7, 8, 9, 10, 11, 12, 13, 14, 15 -> "Aguaceiros"
            16, 17 -> "Nevoeiro"
            18, 21, 22 -> "Neve"
            19, 20, 23 -> "Trovoada"
            else -> "Sem informa\\u00e7\\u00e3o"
        }
    }

    // =========================================================
    // CONTROLO DE INATIVIDADE E SLIDESHOW
    // =========================================================

    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            delay(inactivityDuration)
            onInactivityElapsed()
        }
    }

    /** Após 60 s: documento → vista eventos no início; já em eventos → recarrega o URL inicial. */
    private fun onInactivityElapsed() {
        when (_destaqueModo.value) {
            DestaqueModo.DOCUMENTO -> enterIdleMode()
            DestaqueModo.VISTA_EVENTOS -> {
                _webResetTrigger.value++
                startInactivityTimer()
            }
        }
    }

    private fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (isActive) {
                delay(slideshowInterval)
                nextSlideshowStep()
            }
        }
    }

    private fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
    }

    private fun nextSlideshowStep() {
        val docs = _boardData.value?.documentos ?: return
        if (docs.isEmpty()) return
        slideshowIndex = (slideshowIndex + 1) % docs.size
        val doc = docs[slideshowIndex]
        _categoriaId.value = doc.categoriaId
        _destaque.value = DestaqueUiState(documento = doc, paginaAtual = 0)
    }

    override fun onCleared() {
        super.onCleared()
        inactivityJob?.cancel()
        slideshowJob?.cancel()
        pollingJob?.cancel()
        ipmaJob?.cancel()
    }
}