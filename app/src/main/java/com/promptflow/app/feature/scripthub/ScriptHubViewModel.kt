package com.promptflow.app.feature.scripthub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.promptflow.app.PromptFlowApp
import com.promptflow.app.core.model.Script
import com.promptflow.app.data.repository.ScriptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScriptHubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScriptRepository(
        (application as PromptFlowApp).database.scriptDao()
    )

    val scripts: StateFlow<List<Script>> = repository.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveScript(title: String, content: String, id: Long = 0) {
        viewModelScope.launch {
            val script = Script(
                id = id,
                title = title.ifBlank { "无标题台本" },
                content = content
            )
            repository.saveScript(script)
        }
    }

    fun deleteScript(id: Long) {
        viewModelScope.launch {
            repository.deleteScript(id)
        }
    }
}
