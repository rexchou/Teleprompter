package com.promptflow.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.promptflow.app.core.model.Script

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val speed: Float = 1.0f,
    val fontSizeSp: Int = 18,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Script = Script(
        id = id,
        title = title,
        content = content,
        speed = speed,
        fontSizeSp = fontSizeSp,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(script: Script): ScriptEntity = ScriptEntity(
            id = script.id,
            title = script.title,
            content = script.content,
            speed = script.speed,
            fontSizeSp = script.fontSizeSp,
            createdAt = script.createdAt,
            updatedAt = script.updatedAt
        )
    }
}
