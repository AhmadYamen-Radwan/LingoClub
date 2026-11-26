package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.content.CuratedDictionary
import com.example.data.content.Week1Words
import com.example.data.content.Week2Words
import com.example.data.model.CefrLevel
import com.example.ui.components.CelebrationConfettiBurst
import com.example.ui.components.CefrBadge
import com.example.ui.components.Trophy3DFigure
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberOnSoft
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.CoralOnSoft
import com.example.ui.theme.CoralSecondary
import com.example.ui.theme.CoralSoft
import com.example.ui.theme.IndigoOnSoft
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoSoft
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintOnSoft
import com.example.ui.theme.MintSoft
import com.example.ui.theme.MintTertiary
import com.example.util.rememberTtsSpeaker
import kotlinx.coroutines.delay

internal data class ArcadeWordItem(
    val id: String,
    val english: String,
    val arabic: String,
    val hint: String,
    val cefrLevel: CefrLevel
)

internal fun buildArcadeWordPool(selectedLevel: CefrLevel?): List<ArcadeWordItem> {
    val combined = buildList {
        CuratedDictionary.entries.forEach { entry ->
            add(
                ArcadeWordItem(
                    id = entry.id,
                    english = entry.english,
                    arabic = entry.arabic,
                    hint = "${entry.partOfSpeech} • ${entry.exampleEn.ifBlank { entry.topic }}",
                    cefrLevel = entry.cefrLevel
                )
            )
        }
        (Week1Words.words + Week2Words.words).forEach { word ->
            add(
                ArcadeWordItem(
                    id = word.id,
                    english = word.word,
                    arabic = word.arabic,
                    hint = word.meaning,
                    cefrLevel = word.cefrLevel
                )
            )
        }
    }.distinctBy { it.english.trim().lowercase() }

    val filtered = combined.filter { selectedLevel == null || it.cefrLevel == selectedLevel }
    return if (filtered.size >= 8) filtered else combined
}

@Composable
internal fun WordDashBlitzGame(
    words: List<ArcadeWordItem>,
    selectedLevel: CefrLevel?,
    onShareScore: (String) -> Unit,
    onFinished: (xp: Long, accuracyPct: Long, count: Int, maxCombo: Int) -> Unit
) {
    val tts = rememberTtsSpeaker()
    val haptic = LocalHapticFeedback.current

    var enToAr by remember { mutableStateOf(true) }
    var roundSeed by remember(selectedLevel, enToAr) { mutableIntStateOf(kotlin.random.Random.nextInt(10000)) }
    val pool = remember(words, roundSeed) { words.shuffled(kotlin.random.Random(roundSeed)) }
    var questionIdx by remember(roundSeed) { mutableIntStateOf(if (pool.isNotEmpty()) kotlin.random.Random.nextInt(pool.size) else 0) }
    var timeLeft by remember(roundSeed) { mutableIntStateOf(45) }
    var score by remember(roundSeed) { mutableIntStateOf(0) }
    var combo by remember(roundSeed) { mutableIntStateOf(0) }
    var maxCombo by remember(roundSeed) { mutableIntStateOf(0) }
    var correctCount by remember(roundSeed) { mutableIntStateOf(0) }
    var attempts by remember(roundSeed) { mutableIntStateOf(0) }
    var fiftyFiftyUsed by remember(roundSeed) { mutableStateOf(false) }
    var freezeUsed by remember(roundSeed) { mutableStateOf(false) }
    var eliminatedOptions by remember(roundSeed, questionIdx) { mutableStateOf(emptySet<String>()) }
    var feedbackRight by remember(roundSeed, questionIdx) { mutableStateOf<Boolean?>(null) }
    var finished by remember(roundSeed) { mutableStateOf(false) }
    var recorded by remember(roundSeed) { mutableStateOf(false) }

    val currentWord = pool[questionIdx % pool.size]
    val options = remember(pool, questionIdx, enToAr) {
        val distractors = pool
            .filter { it.id != currentWord.id }
            .shuffled()
            .take(3)
            .map { if (enToAr) it.arabic else it.english }
        val answer = if (enToAr) currentWord.arabic else currentWord.english
        (distractors + answer).shuffled()
    }

    LaunchedEffect(roundSeed, finished) {
        if (!finished) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            finished = true
        }
    }

    LaunchedEffect(finished) {
        if (finished && !recorded && attempts > 0) {
            recorded = true
            val accuracy = ((correctCount * 100f) / attempts.coerceAtLeast(1)).toLong()
            val earnedXp = (score / 10L).coerceIn(15L, 160L)
            onFinished(earnedXp, accuracy, attempts, maxCombo)
        }
    }

    if (finished) {
        val accuracy = if (attempts > 0) (correctCount * 100) / attempts else 0
        val shoutout = "⚡ Scored $score pts ($correctCount/$attempts, ${maxCombo}x combo) in Word Dash Blitz!"
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Trophy3DFigure(size = 94.dp)
                        Text(
                            text = "Blitz Complete!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$score pts • ${accuracy}% accuracy • ${maxCombo}x best combo",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onShareScore(shoutout) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Brag in Chat", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = { roundSeed++ },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("dash_play_again")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play Again")
                    }
                }
            }
            CelebrationConfettiBurst(
                triggerKey = "dash_win_$roundSeed",
                modifier = Modifier.matchParentSize()
            )
        }
        return
    }

    val timerProgress by animateFloatAsState(
        targetValue = (timeLeft / 45f).coerceIn(0f, 1f),
        animationSpec = tween(350),
        label = "dash_timer"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Direction toggle + HUD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = enToAr,
                    onClick = { enToAr = true },
                    label = { Text("EN → AR") }
                )
                FilterChip(
                    selected = !enToAr,
                    onClick = { enToAr = false },
                    label = { Text("AR → EN") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (combo >= 2) {
                    Surface(color = CoralSoft, shape = RoundedCornerShape(99.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = CoralOnSoft,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${combo}x",
                                color = CoralOnSoft,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Surface(
                    color = if (timeLeft <= 10) CoralSoft else AmberSoft,
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        text = "${timeLeft}s",
                        color = if (timeLeft <= 10) CoralOnSoft else AmberOnSoft,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { timerProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CefrBadge(level = currentWord.cefrLevel)
                    Text(
                        text = "Score: $score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { tts.speak(currentWord.english) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Pronounce word",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = if (enToAr) currentWord.english else currentWord.arabic,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = currentWord.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Answer grid
        options.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { option ->
                    val correctAns = if (enToAr) currentWord.arabic else currentWord.english
                    val isEliminated = option in eliminatedOptions
                    val isCorrectChoice = option == correctAns
                    val cardBg = when {
                        isEliminated -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        feedbackRight != null && isCorrectChoice -> MintSoft
                        feedbackRight == false -> MaterialTheme.colorScheme.surface
                        else -> IndigoSoft
                    }
                    val cardFg = when {
                        isEliminated -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        feedbackRight != null && isCorrectChoice -> MintOnSoft
                        else -> IndigoOnSoft
                    }
                    Surface(
                        color = cardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (feedbackRight != null && isCorrectChoice) MintTertiary else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .clickable(enabled = !isEliminated && feedbackRight == null) {
                                attempts++
                                val hit = option == correctAns
                                feedbackRight = hit
                                if (hit) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    combo++
                                    if (combo > maxCombo) maxCombo = combo
                                    correctCount++
                                    score += 20 + (combo.coerceAtMost(5) * 10)
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    combo = 0
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                color = cardFg,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(feedbackRight, questionIdx) {
            if (feedbackRight != null) {
                delay(420)
                questionIdx++
            }
        }

        // Power-up Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val correctAns = if (enToAr) currentWord.arabic else currentWord.english
                    eliminatedOptions = options.filter { it != correctAns }.take(2).toSet()
                    fiftyFiftyUsed = true
                },
                enabled = !fiftyFiftyUsed && feedbackRight == null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (fiftyFiftyUsed) "50/50 Used" else "50/50 Hint")
            }
            OutlinedButton(
                onClick = {
                    timeLeft += 10
                    freezeUsed = true
                },
                enabled = !freezeUsed,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.AcUnit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (freezeUsed) "Freeze Used" else "+10s Freeze")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordDetectiveGame(
    words: List<ArcadeWordItem>,
    selectedLevel: CefrLevel?,
    onShareScore: (String) -> Unit,
    onFinished: (xp: Long, accuracyPct: Long, count: Int, maxCombo: Int) -> Unit
) {
    val tts = rememberTtsSpeaker()
    val haptic = LocalHapticFeedback.current

    val detectivePool = remember(words) {
        words.filter { item ->
            val clean = item.english.trim()
            clean.length in 4..10 && clean.all { it.isLetter() }
        }.ifEmpty {
            listOf(
                ArcadeWordItem("det_1", "FRIEND", "صديق", "Person you trust", CefrLevel.A1),
                ArcadeWordItem("det_2", "JOURNEY", "رحلة", "Travel from place to place", CefrLevel.A2),
                ArcadeWordItem("det_3", "CONFIDENCE", "الثقة بالنفس", "Belief in your ability", CefrLevel.B1),
                ArcadeWordItem("det_4", "NEGOTIATE", "يتفاوض", "Discuss to reach agreement", CefrLevel.B2)
            )
        }
    }

    var wordIdx by remember(selectedLevel) {
        mutableIntStateOf(if (detectivePool.isNotEmpty()) kotlin.random.Random.nextInt(detectivePool.size) else 0)
    }
    var roundSeed by remember(selectedLevel) { mutableIntStateOf(kotlin.random.Random.nextInt(10000)) }
    val target = remember(detectivePool, wordIdx, roundSeed) {
        detectivePool[wordIdx % detectivePool.size]
    }
    val upperWord = remember(target) { target.english.trim().uppercase() }
    val guessedLetters = remember(target.id, roundSeed) { mutableStateListOf<Char>() }
    var showArabicClue by remember(target.id, roundSeed) { mutableStateOf(false) }
    var recorded by remember(target.id, roundSeed) { mutableStateOf(false) }

    val wrongGuesses = guessedLetters.count { it !in upperWord }
    val maxMistakes = 6
    val livesLeft = (maxMistakes - wrongGuesses).coerceAtLeast(0)
    val won = upperWord.all { it in guessedLetters }
    val lost = livesLeft == 0 && !won
    val isDone = won || lost

    LaunchedEffect(isDone) {
        if (isDone && !recorded) {
            recorded = true
            val accuracy = if (guessedLetters.isNotEmpty()) {
                ((guessedLetters.count { it in upperWord } * 100f) / guessedLetters.size).toLong()
            } else 100L
            val xp = if (won) (20L + livesLeft * 5L) else 8L
            if (won) tts.speak(target.english)
            onFinished(xp, accuracy, 1, if (won) livesLeft else 0)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CefrBadge(level = target.cefrLevel)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(maxMistakes) { idx ->
                                val active = idx < livesLeft
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) MintTertiary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                ) {}
                            }
                        }
                        Text(
                            text = "$livesLeft/$maxMistakes orbs",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (livesLeft <= 2) CoralSecondary else MintTertiary
                        )
                    }

                    Text(
                        text = "Clue: ${target.hint}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    AnimatedVisibility(visible = showArabicClue || isDone) {
                        Surface(color = AmberSoft, shape = RoundedCornerShape(12.dp)) {
                            Text(
                                text = "Arabic: ${target.arabic}",
                                color = AmberOnSoft,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Letter Slots
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        upperWord.forEach { ch ->
                            val revealed = ch in guessedLetters || lost
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    won -> MintSoft
                                    lost && ch !in guessedLetters -> CoralSoft
                                    revealed -> IndigoSoft
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (revealed) IndigoPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.size(width = 36.dp, height = 44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (revealed) ch.toString() else "",
                                        color = when {
                                            won -> MintOnSoft
                                            lost && ch !in guessedLetters -> CoralOnSoft
                                            else -> IndigoOnSoft
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    if (!isDone) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showArabicClue = true },
                                enabled = !showArabicClue,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showArabicClue) "Arabic Shown" else "Arabic Clue", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    val unrevealed = upperWord.firstOrNull { it !in guessedLetters }
                                    if (unrevealed != null) guessedLetters.add(unrevealed)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reveal Letter", maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (isDone) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (won) MintSoft else CoralSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val fg = if (won) MintOnSoft else CoralOnSoft
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (won) Trophy3DFigure(size = 76.dp)
                        Text(
                            text = if (won) "Case Solved! '${target.english}'" else "Mystery Word: ${target.english}",
                            color = fg,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onShareScore("🕵️ Solved '${target.english}' (${target.arabic}) in Word Detective with $livesLeft orbs left!")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Share to Chat")
                            }
                            Button(
                                onClick = {
                                    wordIdx = if (detectivePool.size > 1) {
                                        (wordIdx + 1 + kotlin.random.Random.nextInt(detectivePool.size - 1)) % detectivePool.size
                                    } else 0
                                    roundSeed = kotlin.random.Random.nextInt(10000)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("New Random Word")
                            }
                        }
                    }
                }
            } else {
                // On-screen tactile A-Z grid
                val alphabet = ('A'..'Z').toList()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    alphabet.forEach { ch ->
                        val picked = ch in guessedLetters
                        val isHit = picked && ch in upperWord
                        val bg = when {
                            isHit -> MintSoft
                            picked -> CoralSoft.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val fg = when {
                            isHit -> MintOnSoft
                            picked -> CoralOnSoft.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = bg,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(width = 42.dp, height = 46.dp)
                                .clickable(enabled = !picked) {
                                    guessedLetters.add(ch)
                                    haptic.performHapticFeedback(
                                        if (ch in upperWord) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                                    )
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ch.toString(),
                                    color = fg,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        if (won) {
            CelebrationConfettiBurst(
                triggerKey = "detective_win_${target.id}_$roundSeed",
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

private data class MemoryTile(
    val uid: String,
    val pairId: String,
    val displayText: String,
    val isArabic: Boolean
)

@Composable
internal fun MemoryFlip3DGame(
    words: List<ArcadeWordItem>,
    selectedLevel: CefrLevel?,
    onShareScore: (String) -> Unit,
    onFinished: (xp: Long, accuracyPct: Long, count: Int, maxCombo: Int) -> Unit
) {
    val tts = rememberTtsSpeaker()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density

    var roundSeed by remember(selectedLevel) { mutableIntStateOf(kotlin.random.Random.nextInt(10000)) }
    val tiles = remember(words, roundSeed) {
        val chosen = words.shuffled(kotlin.random.Random(roundSeed)).take(6)
        chosen.flatMap { item ->
            listOf(
                MemoryTile("${item.id}_en", item.id, item.english, isArabic = false),
                MemoryTile("${item.id}_ar", item.id, item.arabic, isArabic = true)
            )
        }.shuffled(kotlin.random.Random(roundSeed + 1))
    }

    var firstPickedUid by remember(roundSeed) { mutableStateOf<String?>(null) }
    var secondPickedUid by remember(roundSeed) { mutableStateOf<String?>(null) }
    val matchedPairIds = remember(roundSeed) { mutableStateListOf<String>() }
    var moves by remember(roundSeed) { mutableIntStateOf(0) }
    var combo by remember(roundSeed) { mutableIntStateOf(0) }
    var maxCombo by remember(roundSeed) { mutableIntStateOf(0) }
    var mismatchLocked by remember(roundSeed) { mutableStateOf(false) }
    var recorded by remember(roundSeed) { mutableStateOf(false) }

    val allMatched = matchedPairIds.size == (tiles.size / 2) && tiles.isNotEmpty()

    LaunchedEffect(secondPickedUid) {
        val firstId = firstPickedUid
        val secondId = secondPickedUid
        if (firstId != null && secondId != null) {
            mismatchLocked = true
            val t1 = tiles.firstOrNull { it.uid == firstId }
            val t2 = tiles.firstOrNull { it.uid == secondId }
            if (t1 != null && t2 != null && t1.pairId == t2.pairId) {
                delay(220)
                matchedPairIds.add(t1.pairId)
                combo++
                if (combo > maxCombo) maxCombo = combo
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                delay(750)
                combo = 0
            }
            firstPickedUid = null
            secondPickedUid = null
            mismatchLocked = false
        }
    }

    LaunchedEffect(allMatched) {
        if (allMatched && !recorded) {
            recorded = true
            val accuracy = ((6f / moves.coerceAtLeast(6)) * 100f).toLong().coerceIn(25L, 100L)
            val xp = (45L - (moves - 6).coerceAtLeast(0) * 2L).coerceIn(15L, 60L)
            onFinished(xp, accuracy, 6, maxCombo)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Matched ${matchedPairIds.size} / 6 pairs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = IndigoSoft, shape = RoundedCornerShape(99.dp)) {
                        Text(
                            text = "Moves: $moves",
                            color = IndigoOnSoft,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    if (combo >= 2) {
                        Surface(color = AmberSoft, shape = RoundedCornerShape(99.dp)) {
                            Text(
                                text = "${combo}x Combo",
                                color = AmberOnSoft,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // 3x4 Grid of 3D flipping cards
            tiles.chunked(3).forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTiles.forEach { tile ->
                        val isMatched = tile.pairId in matchedPairIds
                        val isFaceUp = isMatched || tile.uid == firstPickedUid || tile.uid == secondPickedUid
                        val rotation by animateFloatAsState(
                            targetValue = if (isFaceUp) 180f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "mem_flip_${tile.uid}"
                        )
                        val showingFront = rotation > 90f

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isMatched -> MintSoft
                                    showingFront && tile.isArabic -> AmberSoft
                                    showingFront -> IndigoSoft
                                    else -> IndigoPrimary
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (showingFront) 2.dp else 5.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(98.dp)
                                .graphicsLayer {
                                    rotationY = rotation
                                    cameraDistance = 12f * density
                                }
                                .clickable(enabled = !mismatchLocked && !isFaceUp) {
                                    if (!tile.isArabic) tts.speak(tile.displayText)
                                    if (firstPickedUid == null) {
                                        firstPickedUid = tile.uid
                                    } else if (secondPickedUid == null) {
                                        secondPickedUid = tile.uid
                                        moves++
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .graphicsLayer {
                                        if (showingFront) rotationY = 180f
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!showingFront) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = "Hidden memory card",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = if (tile.isArabic) "AR" else "EN",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when {
                                                isMatched -> MintOnSoft.copy(alpha = 0.75f)
                                                tile.isArabic -> AmberOnSoft.copy(alpha = 0.75f)
                                                else -> IndigoOnSoft.copy(alpha = 0.75f)
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = tile.displayText,
                                            color = when {
                                                isMatched -> MintOnSoft
                                                tile.isArabic -> AmberOnSoft
                                                else -> IndigoOnSoft
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = allMatched) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MintSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Trophy3DFigure(size = 82.dp)
                        Text(
                            text = "Board Cleared in $moves Moves!",
                            color = MintOnSoft,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onShareScore("🧠 Cleared the 3D Memory Arena in $moves moves (${maxCombo}x combo)!")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Share Score")
                            }
                            Button(
                                onClick = { roundSeed = kotlin.random.Random.nextInt(10000) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Shuffle Board")
                            }
                        }
                    }
                }
            }
        }

        if (allMatched) {
            CelebrationConfettiBurst(
                triggerKey = "mem_win_$roundSeed",
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
