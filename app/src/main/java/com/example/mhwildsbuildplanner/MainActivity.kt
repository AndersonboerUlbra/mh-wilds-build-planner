package com.example.mhwildsbuildplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF101114)
private val SurfaceDark = Color(0xFF1D1F24)
private val Gold = Color(0xFFFFB547)
private val Muted = Color(0xFFB8BBC4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Gold,
                    background = Background,
                    surface = SurfaceDark,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                BuildPlannerApp()
            }
        }
    }
}

private val skillNames = listOf(
    "Ataque", "Afinidade", "Foco",
    "Defesa", "Exploração", "Resistência"
)

// Níveis ilustrativos para o protótipo; não representam
// uma tabela completa dos níveis máximos reais do jogo.
private val skillMaxLevels = listOf(5, 5, 3, 3, 3, 3)

private val talismanNames = listOf(
    "Talismã do Caçador — Ataque +2",
    "Talismã da Precisão — Afinidade +2",
    "Talismã do Foco — Foco +1"
)

@Composable
fun BuildPlannerApp() {
    var screen by remember { mutableStateOf("home") }
    val levels = remember { mutableStateListOf(0, 0, 0, 0, 0, 0) }
    val selectedTalismans = remember { mutableStateListOf<Int>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "MH WILDS BUILD PLANNER",
                color = Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            when (screen) {
                "home" -> {
                    Spacer(Modifier.height(45.dp))
                    Text(
                        "Monte sua próxima build",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Planeje habilidades e equipamentos usando os talismãs disponíveis.",
                        color = Muted,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(20.dp))

                    ActionButton("Montar Build") {
                        screen = "skills"
                    }
                    ActionButton("Meus Talismãs") {
                        screen = "talismans"
                    }
                    ActionButton("Builds Salvas") {
                        screen = "saved"
                    }
                }

                "skills" -> {
                    Text("Selecionar habilidades", fontSize = 25.sp,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Escolha o nível desejado para cada habilidade. Nível 0 significa que ela não foi selecionada.",
                        color = Muted
                    )
                    Text(
                        "${levels.count { it > 0 }} habilidades selecionadas",
                        color = Gold
                    )

                    skillNames.forEachIndexed { index, name ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = SurfaceDark
                            )
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Nível ${levels[index]} / ${skillMaxLevels[index]}",
                                        color = Muted
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = {
                                                if (levels[index] > 0)
                                                    levels[index]--
                                            },
                                            enabled = levels[index] > 0,
                                            contentPadding = PaddingValues(
                                                horizontal = 15.dp
                                            )
                                        ) { Text("−") }

                                        Spacer(Modifier.width(8.dp))

                                        Button(
                                            onClick = {
                                                if (levels[index] < skillMaxLevels[index])
                                                    levels[index]++
                                            },
                                            enabled = levels[index] < skillMaxLevels[index],
                                            contentPadding = PaddingValues(
                                                horizontal = 15.dp
                                            )
                                        ) { Text("+") }
                                    }
                                }
                            }
                        }
                    }
                    ActionButton("Continuar") { screen = "talismans" }
                }

                "talismans" -> {
                    Text("Meus Talismãs", fontSize = 25.sp,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Selecione os talismãs que você possui. Os dados abaixo são exemplos.",
                        color = Muted
                    )

                    talismanNames.forEachIndexed { index, name ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = SurfaceDark
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = index in selectedTalismans,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (index !in selectedTalismans)
                                                selectedTalismans.add(index)
                                        } else {
                                            selectedTalismans.remove(index)
                                        }
                                    }
                                )
                                Text(name, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Text(
                        "${selectedTalismans.size} talismãs selecionados",
                        color = Gold
                    )
                    ActionButton("Gerar Build") { screen = "results" }
                }

                "results" -> {
                    Text("Build encontrada", fontSize = 27.sp,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Resultado demonstrativo do protótipo",
                        color = Gold
                    )
                    Text(
                        "Habilidades desejadas",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val activeSkills = skillNames.indices.filter {
                        levels[it] > 0
                    }

                    if (activeSkills.isEmpty()) {
                        Text(
                            "Nenhuma habilidade foi selecionada. Você pode voltar e escolher as habilidades desejadas.",
                            color = Muted
                        )
                    } else {
                        activeSkills.forEach { index ->
                            Text(
                                "✓ ${skillNames[index]} Nv. ${levels[index]}",
                                color = Color(0xFF91D9A0)
                            )
                        }
                    }

                    HorizontalDivider(color = Muted)
                    Text(
                        "Equipamentos de exemplo",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    listOf(
                        "Arma" to "Espada Longa",
                        "Cabeça" to "Equipamento Alpha",
                        "Peitoral" to "Equipamento Alpha",
                        "Braços" to "Equipamento Beta",
                        "Cintura" to "Equipamento Alpha",
                        "Pernas" to "Equipamento Beta"
                    ).forEach { (type, item) ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = SurfaceDark
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(type, color = Muted)
                                Text(item)
                            }
                        }
                    }

                    Text(
                        "Talismãs selecionados: ${
                            if (selectedTalismans.isEmpty()) "Nenhum"
                            else selectedTalismans.joinToString {
                                talismanNames[it].substringBefore(" —")
                            }
                        }",
                        color = Gold
                    )

                    Text(
                        "Observação: esta versão usa equipamentos fictícios para demonstrar o fluxo. A compatibilidade real ainda não é calculada.",
                        color = Muted,
                        fontSize = 13.sp
                    )

                    ActionButton("Voltar aos talismãs") {
                        screen = "talismans"
                    }
                    ActionButton("Voltar ao início") {
                        screen = "home"
                    }
                }

                "saved" -> {
                    Text("Builds Salvas", fontSize = 25.sp,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Ainda não há builds salvas nesta versão do MVP.",
                        color = Muted
                    )
                    ActionButton("Montar Build") { screen = "skills" }
                }
            }
        }

        if (screen != "home") {
            HorizontalDivider(color = SurfaceDark)
            TextButton(
                onClick = {
                    screen = when (screen) {
                        "skills" -> "home"
                        "talismans" -> "skills"
                        "results" -> "talismans"
                        else -> "home"
                    }
                },
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text("← Voltar", color = Gold)
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color(0xFF17130D)
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}