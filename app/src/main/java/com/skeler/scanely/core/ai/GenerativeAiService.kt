package com.skeler.scanely.core.ai

import android.net.Uri
import android.os.SystemClock
import com.skeler.scanely.core.network.NetworkObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// Sequences the AI pipeline collaborators (payload, resolver, executor) into
// user-facing AiEvents. All work runs on Dispatchers.IO.
@Singleton
class GenerativeAiService @Inject internal constructor(
    private val payloadFactory: PayloadFactory,
    private val resolver: ProviderConfigResolver,
    private val executor: ProviderExecutor,
    private val networkObserver: NetworkObserver
) {
    // Emits stages, streamed deltas, terminal Finished. Cancelling the collector cancels the work.
    fun processImageEvents(uri: Uri, mode: AiMode, provider: AiProvider): Flow<AiEvent> =
        channelFlow {
            val totalStart = SystemClock.elapsedRealtime()
            send(AiEvent.Stage(AiStage.PREPARING))

            if (!networkObserver.isCurrentlyOnline()) {
                send(AiEvent.Finished(AiResult.Error(OFFLINE_MESSAGE)))
                return@channelFlow
            }

            val payload = try {
                payloadFactory.create(uri, mode)
            } catch (e: PayloadFactory.PayloadException) {
                send(AiEvent.Finished(AiResult.Error(e.message ?: "Failed to read file")))
                return@channelFlow
            }

            val chain = resolver.chain(provider)
            if (chain.isEmpty()) {
                send(AiEvent.Finished(missingConfigError(provider)))
                return@channelFlow
            }

            val exhausted = mutableListOf<Pair<String, Boolean>>()
            chain.forEachIndexed { index, (prov, config) ->
                if (index > 0) {
                    val (prevName, prevNetwork) = exhausted.last()
                    send(
                        AiEvent.Stage(
                            AiStage.UPLOADING,
                            if (prevNetwork) {
                                "Connection trouble with $prevName — trying ${prov.displayName}…"
                            } else {
                                "$prevName is busy — trying ${prov.displayName}…"
                            }
                        )
                    )
                }
                val outcome = executor.run(
                    name = prov.displayName,
                    config = config,
                    systemInstruction = AiPrompts.SYSTEM_INSTRUCTION,
                    prompt = payload.prompt,
                    images = payload.images,
                    allowStreaming = true,
                    emit = { send(it) }
                )
                when (outcome) {
                    is ProviderOutcome.Success -> {
                        send(AiEvent.Stage(AiStage.COMPLETE))
                        aiDebug { "total ${SystemClock.elapsedRealtime() - totalStart} ms" }
                        send(AiEvent.Finished(AiResult.Success(outcome.text)))
                        return@channelFlow
                    }
                    is ProviderOutcome.Fatal -> {
                        send(AiEvent.Finished(outcome.error))
                        return@channelFlow
                    }
                    is ProviderOutcome.Exhausted -> {
                        if (outcome.networkProblem && !networkObserver.isCurrentlyOnline()) {
                            send(AiEvent.Finished(AiResult.Error(OFFLINE_MESSAGE)))
                            return@channelFlow
                        }
                        exhausted.add(prov.displayName to outcome.networkProblem)
                    }
                }
            }

            val primaryUsesBundled = chain.first().second.usesBundledKey
            send(AiEvent.Finished(AiResult.Error(exhaustedMessage(provider, exhausted, primaryUsesBundled))))
        }.flowOn(Dispatchers.IO)

    // Providers on the bundled key; the shared free-tier rate limit applies to these only.
    fun bundledKeyProviders(): Flow<Set<AiProvider>> = resolver.bundledKeyProviders()

    val bundledCapableProviders: Set<AiProvider> get() = resolver.bundledCapableProviders

    // One-shot variant of processImageEvents: only the terminal result.
    suspend fun processImage(uri: Uri, mode: AiMode, provider: AiProvider): AiResult {
        var final: AiResult = AiResult.Error("No response generated")
        processImageEvents(uri, mode, provider).collect { event ->
            if (event is AiEvent.Finished) final = event.result
        }
        return final
    }

    // Non-streaming: translations are short and arrive quickly.
    suspend fun translateText(
        text: String,
        targetLanguage: String,
        provider: AiProvider
    ): AiResult = withContext(Dispatchers.IO) {
        if (!networkObserver.isCurrentlyOnline()) {
            return@withContext AiResult.Error(OFFLINE_MESSAGE)
        }
        val resolved = resolver.resolve(provider) ?: return@withContext missingConfigError(provider)
        // OCR endpoint can't translate; use Mistral's chat API for that.
        val config = if (resolved.kind == ProviderKind.MISTRAL_OCR) {
            ProviderConfig.mistralChat(resolved.apiKey)
        } else resolved
        val outcome = executor.run(
            name = provider.displayName,
            config = config,
            systemInstruction = null,
            prompt = AiPrompts.translate(text, targetLanguage),
            images = emptyList(),
            allowStreaming = false,
            emit = {}
        )
        when (outcome) {
            is ProviderOutcome.Success -> AiResult.Success(outcome.text)
            is ProviderOutcome.Fatal -> outcome.error
            is ProviderOutcome.Exhausted -> AiResult.Error(
                if (outcome.networkProblem && !networkObserver.isCurrentlyOnline()) {
                    OFFLINE_MESSAGE
                } else {
                    "${provider.displayName} is rate-limited right now. Try again shortly."
                }
            )
        }
    }

    private fun missingConfigError(provider: AiProvider): AiResult.Error = AiResult.Error(
        "${provider.displayName} isn't set up yet.\n\n" +
            "Add your ${provider.displayName} details in Settings → AI Providers."
    )

    // Always names the selected provider, never a silent fallback in the chain.
    // [usesBundledKey] distinguishes the shared free-tier key hitting its limit
    // from the user's own key hitting its limit, so the advice matches reality.
    private fun exhaustedMessage(
        provider: AiProvider,
        exhausted: List<Pair<String, Boolean>>,
        usesBundledKey: Boolean
    ): String {
        val name = provider.displayName
        return when {
            exhausted.isNotEmpty() && exhausted.all { it.second } ->
                "Couldn't reach $name — your connection looks unstable. " +
                    "Check it and try again."
            // The user's own key is exhausted: never blame the shared tier or
            // suggest adding a key they already added.
            !usesBundledKey ->
                "Your $name API key has hit its rate or usage limit. Check your " +
                    "plan or billing with $name, then try again. Your key is still " +
                    "used exactly — the app never falls back to a bundled key. To " +
                    "let other configured providers take over automatically, enable " +
                    "fallback in Settings → AI Providers."
            else ->
                "$name is rate-limited right now. Free tiers get busy at peak " +
                    "times — wait a minute and rescan, or add your own API " +
                    "key in Settings → AI Providers for faster, more " +
                    "reliable scans."
        }
    }

    companion object {
        private const val OFFLINE_MESSAGE =
            "No internet connection. Check your network and try again."
    }
}
