package com.statsig.androidsdk

/**
 * A helper class for interfacing with Initialize Response, exposing the full
 * [InitializeResponse.SuccessfulInitializeResponse] instead of a JSON string.
 */
class FullExternalInitializeResponse(
    private val values: InitializeResponse.SuccessfulInitializeResponse?,
    private val evaluationDetails: EvaluationDetails
) {
    internal companion object {
        fun getUninitialized(): FullExternalInitializeResponse = FullExternalInitializeResponse(
            null,
            EvaluationDetails(EvaluationReason.Uninitialized, lcut = 0)
        )
    }
    fun getInitializeResponse(): InitializeResponse.SuccessfulInitializeResponse? = values

    fun getEvaluationDetails(): EvaluationDetails = evaluationDetails.copy()
}
