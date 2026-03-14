package cn.coostack.cooparticlesapi.renderer.effects

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability

class FrameEffectStack(
    private val backendCapabilities: Set<RenderBackendCapability>
) : FrameEffectCollector {
    private val submissions = mutableListOf<IndexedSubmission>()
    private var nextSequence = 0L

    override fun submit(effect: FrameEffectSubmission) {
        submissions += IndexedSubmission(sequence = nextSequence++, submission = effect)
    }

    fun execute() {
        orderedSubmissions().forEach { indexed ->
            indexed.submission.render()
        }
    }

    private fun orderedSubmissions(): List<IndexedSubmission> {
        return submissions
            .asSequence()
            .filter { indexed ->
                backendCapabilities.containsAll(indexed.submission.requiredCapabilities)
            }
            .sortedWith(
                compareBy<IndexedSubmission> { it.submission.priority }
                    .thenBy { it.submission.effectId }
                    .thenBy { it.submission.sourceInstanceId }
                    .thenBy { it.sequence }
            )
            .toList()
    }

    private data class IndexedSubmission(
        val sequence: Long,
        val submission: FrameEffectSubmission
    )
}
