package hack.pune.iqoo.bloomlens.llm

/**
 * GBNF grammar constraining generation to a single JSON object matching [TutorTurn].
 * Passed as [com.geniex.sdk.bean.SamplerConfig.grammarString]. [TutorResponseParser] remains
 * the fallback if the active backend/model ignores grammar constraints.
 */
object TutorGrammar {
    const val GBNF = """
root ::= "{" ws "\"recognized\"" ws ":" ws boolean "," ws "\"bloomLevel\"" ws ":" ws string "," ws "\"feedback\"" ws ":" ws string "," ws "\"message\"" ws ":" ws string "," ws "\"isComplete\"" ws ":" ws boolean "," ws "\"isDevilsAdvocate\"" ws ":" ws boolean ws "}"
boolean ::= "true" | "false"
string ::= "\"" ([^"\\] | "\\" .)* "\""
ws ::= [ \t\n]*
"""
}
