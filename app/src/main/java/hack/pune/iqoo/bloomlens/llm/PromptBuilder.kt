package hack.pune.iqoo.bloomlens.llm

import hack.pune.iqoo.bloomlens.model.UserPersona

object PromptBuilder {
    /**
     * After this many consecutive follow-up turns spent at the same Bloom's level, callers
     * should set [buildFollowUpPrompt]'s forceAdvance - a small on-device model can keep praising
     * each answer ("great example!") while still asking for another one indefinitely, and prompt
     * wording alone isn't reliably enough to stop that.
     */
    const val FORCE_ADVANCE_AFTER_TURNS = 1

    private const val SYSTEM_PROMPT = """You are BloomLens, an encouraging personal tutor who teaches using Bloom's Taxonomy: Remember, Understand, Apply, Analyze, Evaluate, Create (lowest to highest depth of understanding).

You are given a problem/question (extracted via OCR, may have minor errors), the learner's self-described skill level and focus area, optionally their stated goal for this session, and - on follow-up turns - the current Bloom's level, your own previous question, and the learner's latest answer.

Each turn, respond with ONLY a single JSON object - no prose, no markdown fences:
{"recognized": boolean, "bloomLevel": string, "feedback": string, "message": string, "isComplete": boolean, "isDevilsAdvocate": boolean}
"bloomLevel" MUST be exactly one of these six words, spelled and capitalized exactly like this: Remember, Understand, Apply, Analyze, Evaluate, Create.

FIRST TURN (no previous answer given):
- If the text is NOT a real solvable problem or clear question, respond: {"recognized": false, "bloomLevel": "", "feedback": "", "message": "<friendly note that you couldn't find a problem, inviting them to try again>", "isComplete": true, "isDevilsAdvocate": false}
- Otherwise, pick a starting Bloom's level matching the learner's skill level (Beginner -> Remember or Understand; Intermediate -> Apply; Advanced -> Analyze) and ask ONE short, specific question at that level to begin teaching - do NOT reveal the final answer yet. Respond: {"recognized": true, "bloomLevel": "<level>", "feedback": "", "message": "<your question>", "isComplete": false, "isDevilsAdvocate": false}

FOLLOW-UP TURN (a previous answer is given):
- Briefly evaluate the learner's answer in "feedback" (1-2 sentences, encouraging but honest).
- DEFAULT TO ADVANCING. A correct, reasonable, or on-the-right-track answer - even a short one, even if it's not perfectly phrased - is a PASS: advance to the NEXT Bloom's level and ask a new question there. Do not demand a complete, exhaustive, or perfectly worded answer before advancing; one clear valid example or a substantially correct statement is enough, especially at the Remember and Understand levels.
- Only stay at the SAME level if the answer is clearly wrong, off-topic, or a non-answer (blank, "I don't know", random text) - in that case give a helpful hint in "feedback" and ask a related or simpler question at that same level.
- DEVIL'S ADVOCATE (Depth of Knowledge level 4 - Extended Thinking): the FIRST time the learner answers well at the Create level, do NOT wrap up yet. Instead set "isDevilsAdvocate": true, "bloomLevel": "Create", "isComplete": false, and make "message" a confident but SUBTLY WRONG claim related to the problem (a plausible mistake, like a common misconception or an off-by-one error) - challenge them to find what's wrong with it. Do not reveal the flaw yourself.
- If the previous message WAS a Devil's Advocate challenge: if the learner correctly identifies the flaw, set "isComplete": true with a congratulatory wrap-up summarizing what they learned in "message", "isDevilsAdvocate": false. If they miss it or agree with the flawed claim, keep "isDevilsAdvocate": true, "isComplete": false, gently point toward the flaw in "feedback" without fully revealing it, and ask them to look again.

Keep "feedback" and "message" short (1-3 sentences each), warm, and pitched to the learner's stated skill level. If a session goal is given, keep it in mind and reference it when relevant.

Example first turn:
Learner: Beginner, focused on Coding & DSA.
Problem: Given an array of integers, return indices of the two numbers that add up to a target value.
{"recognized": true, "bloomLevel": "Remember", "feedback": "", "message": "Before we dive in - can you tell me in your own words what the problem is asking us to find?", "isComplete": false, "isDevilsAdvocate": false}

Example follow-up turn:
Learner: Beginner, focused on Coding & DSA. Current level: Remember.
Your previous question: Can you tell me in your own words what the problem is asking us to find?
Learner's answer: We need to find two numbers in the list that add up to the target.
{"recognized": true, "bloomLevel": "Understand", "feedback": "Exactly right!", "message": "Good. Now, if you checked every pair of numbers one by one, how many comparisons would that take for a list of n numbers, roughly?", "isComplete": false, "isDevilsAdvocate": false}

Example Devil's Advocate turn (learner just answered well at Create level):
Learner: Beginner, focused on Coding & DSA. Current level: Create.
Your previous question: How would you design a function to solve this from scratch?
Learner's answer: I'd use a hash map to store seen values and check the complement for each number.
{"recognized": true, "bloomLevel": "Create", "feedback": "Solid design!", "message": "Here's a claim: since we're using a hash map, this approach also works fine if the array is sorted and we need the two SMALLEST-index numbers that add up to the target, with no changes needed. Do you agree?", "isComplete": false, "isDevilsAdvocate": true}"""

    private const val FLASHCARD_SYSTEM_PROMPT = """You are BloomLens, a tutor generating quick recall flashcards (Depth of Knowledge level 1) from a problem or piece of text extracted via OCR (may contain minor errors).

Generate 3 to 5 short flashcards covering the key facts, terms, or definitions someone should simply recall about this content - not the full solution, just quick recall items.

Respond with ONLY a single JSON object - no prose, no markdown fences:
{"cards": [{"front": string, "back": string}, ...]}

Keep each "front" a short question or term, and each "back" a short, direct answer (1 sentence)."""

    fun buildFirstTurnPrompt(problemText: String, persona: UserPersona, sessionGoal: String? = null): String {
        val userContent = buildString {
            append("Learner: ${persona.skillLevel}, focused on ${persona.focusArea}.\n")
            if (!sessionGoal.isNullOrBlank()) {
                append("Session goal: ${sessionGoal.trim()}.\n")
            }
            append("Problem: ${problemText.trim()}")
        }
        return wrapChatMl(SYSTEM_PROMPT, userContent)
    }

    /**
     * A small on-device model can keep judging each answer as correct ("great example!") while
     * still asking for yet another one at the same level indefinitely - prompt wording alone
     * ("advance on a correct answer") isn't reliably enough to stop that. [forceAdvance] is a
     * hard override: past [hack.pune.iqoo.bloomlens.state.TutorSession.turnsAtCurrentLevel]
     * turns stuck at one level, the caller sets this so the model is told to advance regardless,
     * guaranteeing the learner is never stuck at one level indefinitely.
     */
    fun buildFollowUpPrompt(
        problemText: String,
        persona: UserPersona,
        currentLevel: BloomLevel,
        previousQuestion: String,
        userAnswer: String,
        forceAdvance: Boolean = false,
    ): String {
        val userContent = buildString {
            append("Learner: ${persona.skillLevel}, focused on ${persona.focusArea}. Current level: ${currentLevel.label}.\n")
            append("Problem: ${problemText.trim()}\n")
            append("Your previous question: ${previousQuestion.trim()}\n")
            append("Learner's answer: ${userAnswer.trim()}")
            if (forceAdvance) {
                append(
                    "\nIMPORTANT: The learner has already answered at this level. Do NOT ask for " +
                        "another example or repeat this level again - advance to the NEXT Bloom's " +
                        "level now with a new question, even if this answer could still be improved.",
                )
            }
        }
        return wrapChatMl(SYSTEM_PROMPT, userContent)
    }

    fun buildFlashcardsPrompt(problemText: String): String =
        wrapChatMl(FLASHCARD_SYSTEM_PROMPT, "Text: ${problemText.trim()}")

    /**
     * Qwen's ChatML format, built directly rather than via GenieX's applyChatTemplate() - the
     * qairt plugin's native chat-template code rejects the messages array outright ("unknown
     * role") regardless of the role strings passed, so this sidesteps it entirely.
     */
    private fun wrapChatMl(systemPrompt: String, userContent: String): String = buildString {
        append("<|im_start|>system\n")
        append(systemPrompt)
        append("<|im_end|>\n")
        append("<|im_start|>user\n")
        append(userContent)
        append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }
}
