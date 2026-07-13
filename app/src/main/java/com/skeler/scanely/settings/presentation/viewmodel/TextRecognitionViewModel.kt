package com.skeler.scanely.settings.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ocr.TextOcrEngine
import com.skeler.scanely.core.ocr.paddle.PackState
import com.skeler.scanely.core.ocr.paddle.PaddleModelStore
import com.skeler.scanely.core.ocr.paddle.ScriptPack
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TextRecognitionViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val store: PaddleModelStore
) : ViewModel() {

    val packStates: StateFlow<Map<ScriptPack, PackState>> = store.states

    val engine: Flow<TextOcrEngine> = settings.getString(SettingsKeys.TEXT_OCR_ENGINE)
        .map { TextOcrEngine.fromId(it) }

    val script: Flow<ScriptPack> = settings.getString(SettingsKeys.PADDLE_SCRIPT)
        .map { ScriptPack.fromId(it) }

    fun toggle(key: SettingsKeys): Flow<Boolean> = settings.getBoolean(key)

    fun setEngine(value: TextOcrEngine) = write { settings.setString(SettingsKeys.TEXT_OCR_ENGINE, value.id) }

    fun setScript(pack: ScriptPack) = write { settings.setString(SettingsKeys.PADDLE_SCRIPT, pack.id) }

    fun setToggle(key: SettingsKeys, value: Boolean) = write { settings.setBoolean(key, value) }

    fun download(pack: ScriptPack) = store.downloadAsync(pack)

    val uvdocState: StateFlow<PackState> = store.uvdocState

    /**
     * The dewarper isn't bundled, so enabling it pulls the model first. Both the download and
     * the flag it sets live on the store's scope: closing this screen mid-download would
     * otherwise cancel the write and leave the model installed with the setting still off.
     */
    fun setDewarp(enabled: Boolean) {
        if (uvdocState.value is PackState.Downloading) return
        if (!enabled) {
            write { settings.setBoolean(SettingsKeys.PADDLE_DOC_UNWARP, false) }
            return
        }
        store.downloadUvdocAsync {
            settings.setBoolean(SettingsKeys.PADDLE_DOC_UNWARP, true)
        }
    }

    val tableState: StateFlow<PackState> = store.tableState

    /**
     * Layout detection is bundled, but the table model isn't — tables are on exactly when
     * SLANet is installed, so the switch is the download.
     */
    fun setTables(enabled: Boolean) {
        // Deleting the file a download is still writing would leave it installed anyway.
        if (tableState.value is PackState.Downloading) return
        if (enabled) store.downloadTableAsync() else write { store.deleteTable() }
    }

    /** Deleting the selected pack would leave no model, so fall back to the bundled one. */
    fun delete(pack: ScriptPack) = write {
        store.delete(pack)
        val selected = ScriptPack.fromId(settings.getString(SettingsKeys.PADDLE_SCRIPT).first())
        if (selected == pack) settings.setString(SettingsKeys.PADDLE_SCRIPT, ScriptPack.DEFAULT.id)
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}
