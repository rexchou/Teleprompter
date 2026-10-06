package com.promptflow.app.data.repository

import com.promptflow.app.core.model.Script
import com.promptflow.app.data.local.ScriptDao
import com.promptflow.app.data.local.ScriptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ScriptRepository(private val scriptDao: ScriptDao) {

    val allScripts: Flow<List<Script>> = scriptDao.getAllScripts().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getScriptById(id: Long): Script? = withContext(Dispatchers.IO) {
        scriptDao.getScriptById(id)?.toDomain()
    }

    suspend fun saveScript(script: Script): Long = withContext(Dispatchers.IO) {
        val entity = ScriptEntity.fromDomain(script.copy(updatedAt = System.currentTimeMillis()))
        if (script.id == 0L) {
            scriptDao.insertScript(entity)
        } else {
            scriptDao.updateScript(entity)
            script.id
        }
    }

    suspend fun deleteScript(id: Long) = withContext(Dispatchers.IO) {
        scriptDao.deleteScriptById(id)
    }
}
