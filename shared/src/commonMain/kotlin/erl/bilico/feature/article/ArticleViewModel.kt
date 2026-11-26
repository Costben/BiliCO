package erl.bilico.feature.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.ArticleDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArticleUiState(
    val article: ArticleDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ArticleViewModel : ViewModel() {
    private val _state = MutableStateFlow(ArticleUiState())
    val state: StateFlow<ArticleUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var loadedKey: Any? = null

    fun load(key: Any, loader: suspend () -> Result<ArticleDetail>) {
        if (key == loadedKey && _state.value.article != null) return
        loadedKey = key
        job?.cancel()
        _state.value = ArticleUiState(isLoading = true)
        job = viewModelScope.launch {
            val result = loader()
            if (result.isFailure && result.exceptionOrNull() is CancellationException) return@launch
            _state.value = result.fold(
                onSuccess = { ArticleUiState(article = it, isLoading = false) },
                onFailure = { ArticleUiState(isLoading = false, error = it.message ?: "内容加载失败") },
            )
        }
    }

    fun retry(loader: suspend () -> Result<ArticleDetail>) {
        loadedKey = null
        load(loadedKey ?: Unit, loader)
    }

    fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }
}
