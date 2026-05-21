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

data class DestaqueUiState(
    val documento: DocumentoModel? = null,
    val paginaAtual: Int = 0
)

class MainViewModel : ViewModel() {

    // --- Dados da API interna ---
    private val _boardData = MutableStateFlow<BoardData?>(null)
    val boardData: StateFlow<BoardData?> = _boardData.asStateFlow()

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
        startInactivityTimer()
        startIpmaPolling() // Corrigido: Estava duplicado na tua versão
    }

    // =========================================================
    // FUNÇÕES PÚBLICAS
    // =========================================================

    fun resetInactivityTimer() {
        if (_appMode.value != AppMode.MANUAL) {
            _appMode.value = AppMode.MANUAL
            stopSlideshow()
        }
        startInactivityTimer()
    }

    fun selecionarCategoria(categoriaId: String) {
        resetInactivityTimer()
        _categoriaId.value = categoriaId
        val docs = _boardData.value?.documentos
            ?.filter { it.categoriaId == categoriaId }
            ?: emptyList()
        _destaque.value = DestaqueUiState(
            documento = docs.firstOrNull(),
            paginaAtual = 0
        )
    }

    fun selecionarDocumento(doc: DocumentoModel) {
        resetInactivityTimer()
        _destaque.value = DestaqueUiState(documento = doc, paginaAtual = 0)
    }

    fun avancarPagina() {
        resetInactivityTimer()
        val estado = _destaque.value
        val maxPaginas = estado.documento?.paginas?.size ?: 1
        if (estado.paginaAtual < maxPaginas - 1) {
            _destaque.value = estado.copy(paginaAtual = estado.paginaAtual + 1)
        }
    }

    fun recuarPagina() {
        resetInactivityTimer()
        val estado = _destaque.value
        if (estado.paginaAtual > 0) {
            _destaque.value = estado.copy(paginaAtual = estado.paginaAtual - 1)
        }
    }

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
                    .take(6)

                val dataFiltrada = rawData.copy(categorias = categoriasFiltradas)
                _boardData.value = dataFiltrada

                if (_categoriaId.value == null && categoriasFiltradas.isNotEmpty()) {
                    selecionarCategoria(categoriasFiltradas.first().id)
                }
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
            2 -> "Pouco Nublado"
            3 -> "Parc. Nublado"
            4 -> "Muito Nublado"
            5 -> "Céu Encoberto"
            6, 7, 8 -> "Aguaceiros"
            9, 10, 11 -> "Chuva"
            12 -> "Chuva/Neve"
            13, 14 -> "Neve"
            15 -> "Trovoada"
            16, 17 -> "Nevoeiro"
            else -> "Variável"
        }
    }

    // =========================================================
    // CONTROLO DE INATIVIDADE E SLIDESHOW
    // =========================================================

    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            delay(inactivityDuration)
            _appMode.value = AppMode.AUTOMATICO
            startSlideshow()
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