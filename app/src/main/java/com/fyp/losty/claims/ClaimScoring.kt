package com.fyp.losty.claims

/**
 * Calculates a trust score based on how well the claimant's answer matches 
 * the owner's predefined security answer and the presence of photo proof.
 */
fun calculateVerificationScore(
    userAnswer: String, 
    correctAnswer: String, 
    hasPhotoProof: Boolean
): Int {
    var score = 0
    val claimText = userAnswer.lowercase().trim()
    val actualAnswer = correctAnswer.lowercase().trim()

    // 1. Exact or Keyword Matching (Max 60 points)
    if (claimText.contains(actualAnswer) && actualAnswer.isNotEmpty()) {
        // High reward for containing the exact answer string
        score += 60
    } else {
        // Breakdown matching: Split the correct answer into keywords (e.g., "Blue Fossil Wallet")
        val keywords = actualAnswer.split(" ", ",", ".").filter { it.length > 2 }
        if (keywords.isNotEmpty()) {
            val matches = keywords.count { claimText.contains(it) }
            val matchPercentage = matches.toFloat() / keywords.size
            score += (matchPercentage * 50).toInt() // Up to 50 points for partial keyword matches
        }
    }

    // 2. Photo Evidence (The "Gold Standard" - Max 40 points)
    if (hasPhotoProof) {
        score += 40
    }

    // 3. Length/Detail Bonus (Small nudge for effort)
    if (claimText.length > actualAnswer.length + 10) {
        score += 5
    }

    return score.coerceAtMost(100)
}
