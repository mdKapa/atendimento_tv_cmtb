package pt.cmtb.atendimentotv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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

    // --- IPMA: Risco de Incêndio ---
    private val _riscoIncendio = MutableStateFlow(RiscoIncendioState())
    val riscoIncendio: StateFlow<RiscoIncendioState> = _riscoIncendio.asStateFlow()

    // --- Timers e jobs ---
    private var inactivityJob: Job? = null
    private val inactivityDuration = 60_000L

    private var slideshowJob: Job? = null
    private var slideshowIndex = 0
    private val slideshowInterval = 45_000L

    private var lastVersion = -1
    private var pollingJob: Job? = null
    private var ipmaJob: Job? = null

    init {
        fetchBoardData()
        startVersionPolling()
        startInactivityTimer()
        fetchRiscoIncendio()
        startIpmaPolling()
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
    // FUNÇÕES PRIVADAS
    // =========================================================

    private fun fetchBoardData() {
        viewModelScope.launch {
            _isLoading.value = true
            _erro.value = null
            try {
                // 1. Recebemos os dados brutos e normalizados da API
                val rawData = ApiClient.service.fetchBoardData()

                // 2. Extraímos apenas os IDs das categorias que estão associadas a pelo menos 1 documento.
                // Usamos toSet() para eliminar IDs duplicados e garantir procuras ultrarrápidas O(1)
                val idsComDocumentos = rawData.documentos.map { it.categoriaId }.toSet()

                // 3. Filtramos a lista bruta de categorias:
                // - A categoria tem de existir no Set idsComDocumentos
                // - Bloqueamos no máximo a 6 elementos (.take(6)) para garantir as 3x2 linhas na grelha da TV
                val categoriasFiltradas = rawData.categorias
                    .filter { categoria -> categoria.id in idsComDocumentos }
                    .take(6)

                // 4. Criamos uma cópia do BoardData injetando apenas as categorias válidas
                val dataFiltrada = rawData.copy(categorias = categoriasFiltradas)

                // 5. Atualizamos a UI com os dados limpos
                _boardData.value = dataFiltrada

                // 6. Selecionamos a 1ª categoria automaticamente (agora garantimos que ela tem documentos!)
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

    // Busca o risco de incêndio para hoje (RCM) — chamada única imediata
    private fun fetchRiscoIncendio() {
        viewModelScope.launch {
            try {
                // 1. Faz a chamada ao endpoint correto de RCM (idDay = 0)
                val response = IpmaClient.service.fetchRcm(0)

                // 2. Procura na lista o nó que corresponde ao concelho de Terras de Bouro
                // Nota: IpmaRcmResposta.local é um Map<String, IpmaRcmConcelho>
                val concelho = response.local[IpmaClient.DICO_TERRAS_DE_BOURO]

                if (concelho != null) {
                    val rcmValue = concelho.data.rcm // Será um número de 1 a 5

                    // 3. Usa os métodos brilhantes que criaste no IpmaClient para formatar a UI
                    _riscoIncendio.value = RiscoIncendioState(
                        nivel = IpmaClient.nivelTexto(rcmValue),
                        classRisco = rcmValue,
                        local = IpmaClient.NOME_LOCAL,
                        cor = IpmaClient.nivelCor(rcmValue)
                    )
                } else {
                    // DICO não encontrado na lista do IPMA (falha rara na API deles)
                    _riscoIncendio.value = RiscoIncendioState(
                        nivel = "Sem dados",
                        classRisco = 0,
                        local = IpmaClient.NOME_LOCAL,
                        cor = "#888888"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("IPMA", "Erro ao carregar RCM: ${e.message}")
                _riscoIncendio.value = RiscoIncendioState(
                    nivel = "Indisponível",
                    classRisco = 0,
                    local = IpmaClient.NOME_LOCAL,
                    cor = "#888888"
                )
            }
        }
    }

    // O teu Polling de 1 hora está perfeito e não precisa de alterações!
    private fun startIpmaPolling() {
        ipmaJob?.cancel()
        ipmaJob = viewModelScope.launch {
            while (isActive) {
                delay(60 * 60 * 1000L) // 1 hora
                fetchRiscoIncendio()
            }
        }
    }

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