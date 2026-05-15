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

// ============================================================
// GUIA DE EQUIVALÊNCIAS RIVERPOD → VIEWMODEL
//
// @Riverpod(keepAlive:true)          → ViewModel (vive enquanto a Activity existir)
// MutableStateFlow                   → estado mutável interno (privado)
// StateFlow (public, via asStateFlow) → o que a UI observa (só leitura)
// viewModelScope.launch { }          → coroutine ligada ao ciclo de vida
// ============================================================

// Estados possíveis do ecrã — equivalente ao enum AppMode { manual, automatico }
enum class AppMode { MANUAL, AUTOMATICO }

// Estado da UI do documento em destaque — agrupa tudo num só objeto
// Equivalente aos providers: documentoEmDestaqueProvider + paginaAtualProvider
data class DestaqueUiState(
    val documento: DocumentoModel? = null,
    val paginaAtual: Int = 0
)

class MainViewModel : ViewModel() {

    // =========================================================
    // 1. ESTADO DOS DADOS DA API
    //    Equivalente ao boardDataProvider (FutureProvider)
    // =========================================================

    // MutableStateFlow = estado interno mutável (privado, só o ViewModel escreve)
    private val _boardData = MutableStateFlow<BoardData?>(null)
    // StateFlow = estado público (a Activity lê, mas não escreve)
    val boardData: StateFlow<BoardData?> = _boardData.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _erro = MutableStateFlow<String?>(null)
    val erro: StateFlow<String?> = _erro.asStateFlow()

    // =========================================================
    // 2. ESTADO DA CATEGORIA SELECIONADA
    //    Equivalente ao categoriaSelecionadaProvider
    // =========================================================
    private val _categoriaId = MutableStateFlow<String?>(null)
    val categoriaId: StateFlow<String?> = _categoriaId.asStateFlow()

    // =========================================================
    // 3. ESTADO DO DOCUMENTO EM DESTAQUE + PÁGINA ATUAL
    //    Equivalente ao documentoEmDestaqueProvider + paginaAtualProvider
    // =========================================================
    private val _destaque = MutableStateFlow(DestaqueUiState())
    val destaque: StateFlow<DestaqueUiState> = _destaque.asStateFlow()

    // =========================================================
    // 4. MOTOR DE INATIVIDADE
    //    Equivalente ao InactivityNotifier (Riverpod keepAlive)
    // =========================================================
    private val _appMode = MutableStateFlow(AppMode.AUTOMATICO)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private var inactivityJob: Job? = null
    private val inactivityDuration = 60_000L // 1 minuto em ms

    // =========================================================
    // 5. MOTOR DO SLIDESHOW
    //    Equivalente ao SlideshowService
    // =========================================================
    private var slideshowJob: Job? = null
    private var slideshowIndex = 0
    private val slideshowInterval = 45_000L // 45 segundos

    // =========================================================
    // 6. POLLING DE VERSÃO DA API
    //    Equivalente ao boardVersionProvider (StreamProvider)
    // =========================================================
    private var lastVersion = -1
    private var pollingJob: Job? = null

    // init{} corre quando o ViewModel é criado — equivalente ao build() do Riverpod
    init {
        fetchBoardData()
        startVersionPolling()
        startInactivityTimer()
    }

    // =========================================================
    // FUNÇÕES PÚBLICAS — chamadas pela Activity
    // =========================================================

    // Equivalente ao resetTimer() do InactivityNotifier
    fun resetInactivityTimer() {
        if (_appMode.value != AppMode.MANUAL) {
            _appMode.value = AppMode.MANUAL
            stopSlideshow()
        }
        startInactivityTimer()
    }

    // Equivalente ao setCategoria() + lógica do _handleCategorySelection
    fun selecionarCategoria(categoriaId: String) {
        resetInactivityTimer()
        _categoriaId.value = categoriaId

        // Encontra o primeiro documento desta categoria e coloca em destaque
        val docs = _boardData.value?.documentos
            ?.filter { it.categoriaId == categoriaId }
            ?: emptyList()

        _destaque.value = DestaqueUiState(
            documento = docs.firstOrNull(),
            paginaAtual = 0
        )
    }

    // Equivalente ao setDocumento() do DocumentoEmDestaqueNotifier
    fun selecionarDocumento(doc: DocumentoModel) {
        resetInactivityTimer()
        _destaque.value = DestaqueUiState(documento = doc, paginaAtual = 0)
    }

    // Equivalente ao setPagina() do PaginaAtualNotifier
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
    // FUNÇÕES PRIVADAS — lógica interna
    // =========================================================

    // Equivalente ao fetchBoardData() do boardDataProvider
    private fun fetchBoardData() {
        viewModelScope.launch {
            _isLoading.value = true
            _erro.value = null
            try {
                val data = ApiClient.service.fetchBoardData()
                _boardData.value = data

                // Se não há categoria selecionada, seleciona a primeira automaticamente
                if (_categoriaId.value == null && data.categorias.isNotEmpty()) {
                    selecionarCategoria(data.categorias.first().id)
                }
            } catch (e: Exception) {
                _erro.value = "Erro ao carregar dados: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Equivalente ao boardVersionProvider (Stream que verifica a cada 5 minutos)
    private fun startVersionPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val v = ApiClient.service.fetchBoardVersion().version
                    if (lastVersion != -1 && v != lastVersion) {
                        // Versão mudou — recarrega os dados (equivalente ao ref.watch da versão)
                        fetchBoardData()
                    }
                    lastVersion = v
                } catch (_: Exception) { /* ignora erros de rede silenciosamente */ }

                delay(5 * 60 * 1000L) // espera 5 minutos
            }
        }
    }

    // Equivalente ao _startTimer() do InactivityNotifier
    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            delay(inactivityDuration)
            // Tempo esgotado — passa para modo automático e inicia slideshow
            _appMode.value = AppMode.AUTOMATICO
            startSlideshow()
        }
    }

    // Equivalente ao _startSlideshow() do SlideshowService
    private fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (isActive) {
                delay(slideshowInterval)
                nextSlideshowStep()
            }
        }
    }

    // Equivalente ao _stopSlideshow()
    private fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
    }

    // Equivalente ao _nextStep() do SlideshowService
    private fun nextSlideshowStep() {
        val docs = _boardData.value?.documentos ?: return
        if (docs.isEmpty()) return

        slideshowIndex = (slideshowIndex + 1) % docs.size
        val doc = docs[slideshowIndex]

        _categoriaId.value = doc.categoriaId
        _destaque.value = DestaqueUiState(documento = doc, paginaAtual = 0)
    }

    // Limpeza quando a Activity é destruída — equivalente ao ref.onDispose
    override fun onCleared() {
        super.onCleared()
        inactivityJob?.cancel()
        slideshowJob?.cancel()
        pollingJob?.cancel()
    }
}