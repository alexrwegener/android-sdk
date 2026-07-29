package com.statsig.androidsdk

/**
 * A helper class for interfacing with Initialize Response, exposing the full
 * [InitializeResponse.SuccessfulInitializeResponse] instead of a JSON string.
 */
class FullExternalInitializeResponse(
    private val values: InitializeResponse.SuccessfulInitializeResponse?,
    private val evaluationDetails: EvalDetails
) {
    internal companion object {
        fun getUninitialized(): FullExternalInitializeResponse = FullExternalInitializeResponse(
            null,
            EvalDetails(EvalSource.Uninitialized, EvalReason.Unrecognized)
        )
    }
    fun getInitializeResponse(): InitializeResponse.SuccessfulInitializeResponse? = values

    fun getEvalDetails(): EvalDetails = evaluationDetails.copy()
}
