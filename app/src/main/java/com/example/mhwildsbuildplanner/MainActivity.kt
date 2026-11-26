package com.example.mhwildsbuildplanner

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mhwildsbuildplanner.ui.theme.MHWildsBuildPlannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Background = Color(0xFF101114)
private val SurfaceDark = Color(0xFF1D1F24)
private val Gold = Color(0xFFFFB547)
private val Muted = Color(0xFFB8BBC4)
private val Success = Color(0xFF91D9A0)

private const val PREFS_NAME = "mh_wilds_build_planner"
private const val SAVED_BUILDS_KEY = "saved_builds_v1"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MHWildsBuildPlannerTheme(darkTheme = true, dynamicColor = false) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    BuildPlannerApp()
                }
            }
        }
    }
}

private data class SkillDefinition(
    val id: String,
    val apiId: Int,
    val name: String,
    val maxLevel: Int,
    val description: String
)

private data class Talisman(
    val id: String,
    val name: String,
    val bonuses: Map<String, Int>
)

private data class Equipment(
    val id: String,
    val slot: String,
    val name: String,
    val bonuses: Map<String, Int>,
    val rarity: Int,
    val defense: Int
)

private data class BuildConfiguration(
    val id: Long,
    val desiredLevels: Map<String, Int>,
    val talisman: Talisman?,
    val equipment: List<Equipment>
)

private data class ApiCatalog(
    val skills: List<SkillDefinition>,
    val talismans: List<Talisman>,
    val equipment: List<Equipment>
)

private val equipmentSlots = listOf("Cabeça", "Peitoral", "Braços", "Cintura", "Pernas")

private object MhWildsApi {
    private const val BASE_URL = "https://wilds.mhdb.io/pt"

    private fun getArray(baseUrl: String, path: String): JSONArray {
        val connection = (URL("$baseUrl/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MHWildsBuildPlanner/1.0")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (status !in 200..299) {
                throw IllegalStateException("A API respondeu com HTTP $status.")
            }
            JSONArray(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun localizedText(
        primary: JSONObject?,
        fallback: JSONObject?,
        field: String,
        default: String
    ): String {
        val primaryValue = primary?.optString(field, null)?.takeIf {
            !it.isNullOrBlank() && it != "null"
        }
        val fallbackValue = fallback?.optString(field, null)?.takeIf {
            !it.isNullOrBlank() && it != "null"
        }
        return primaryValue ?: fallbackValue ?: default
    }

    fun loadCatalog(): ApiCatalog {
        val query = URLEncoder.encode("""{"rank":"high"}""", "UTF-8")
        val armorJson = getArray(BASE_URL, "armor?q=$query")
        val skillsJson = getArray(BASE_URL, "skills")
        val charmsJson = getArray(BASE_URL, "charms")

        val englishBase = "https://wilds.mhdb.io/en"
        val englishArmorJson = getArray(englishBase, "armor?q=$query")
        val englishSkillsJson = getArray(englishBase, "skills")
        val englishCharmsJson = getArray(englishBase, "charms")

        val englishArmorById = (0 until englishArmorJson.length())
            .mapNotNull { englishArmorJson.optJSONObject(it) }
            .associateBy { it.optInt("id", -1) }

        val englishSkillsById = (0 until englishSkillsJson.length())
            .mapNotNull { englishSkillsJson.optJSONObject(it) }
            .associateBy { it.optInt("id", -1) }

        val englishCharmRanksById = buildMap<Int, JSONObject> {
            for (i in 0 until englishCharmsJson.length()) {
                val charm = englishCharmsJson.optJSONObject(i) ?: continue
                val ranks = charm.optJSONArray("ranks") ?: JSONArray()
                for (j in 0 until ranks.length()) {
                    val rank = ranks.optJSONObject(j) ?: continue
                    put(rank.optInt("id", -1), rank)
                }
            }
        }

        val highRankArmor = (0 until armorJson.length())
            .mapNotNull { armorJson.optJSONObject(it) }
            .filter { it.optString("rank") == "high" }

        val rawCharms = mutableListOf<Talisman>()
        for (i in 0 until charmsJson.length()) {
            val charm = charmsJson.optJSONObject(i) ?: continue
            val ranks = charm.optJSONArray("ranks") ?: JSONArray()
            for (j in 0 until ranks.length()) {
                val rank = ranks.optJSONObject(j) ?: continue
                val rankId = rank.optInt("id", -1)
                if (rankId < 0) continue
                rawCharms += Talisman(
                    id = rankId.toString(),
                    name = localizedText(
                        rank,
                        englishCharmRanksById[rankId],
                        "name",
                        "Talismã #$rankId"
                    ),
                    bonuses = parseSkillBonuses(rank.optJSONArray("skills"))
                )
            }
        }

        val rawArmor = highRankArmor.mapNotNull { armor ->
            val id = armor.optInt("id", -1)
            if (id < 0) return@mapNotNull null
            val slot = when (armor.optString("kind")) {
                "head" -> "Cabeça"
                "chest" -> "Peitoral"
                "arms" -> "Braços"
                "waist" -> "Cintura"
                "legs" -> "Pernas"
                else -> return@mapNotNull null
            }
            Equipment(
                id = id.toString(),
                slot = slot,
                name = localizedText(armor, englishArmorById[id], "name", "Armadura #$id"),
                bonuses = parseSkillBonuses(armor.optJSONArray("skills")),
                rarity = armor.optInt("rarity", 0),
                defense = armor.optJSONObject("defense")?.optInt("max", 0) ?: 0
            )
        }

        val usedSkillIds = (
                rawArmor.flatMap { it.bonuses.keys } +
                        rawCharms.flatMap { it.bonuses.keys }
                ).toSet()

        val skillDefinitions = (0 until skillsJson.length()).mapNotNull { index ->
            val skill = skillsJson.optJSONObject(index) ?: return@mapNotNull null
            val id = skill.optInt("id", -1)
            if (id < 0 || id.toString() !in usedSkillIds) return@mapNotNull null
            val ranks = skill.optJSONArray("ranks") ?: JSONArray()
            val maxLevel = (0 until ranks.length())
                .mapNotNull { ranks.optJSONObject(it)?.optInt("level") }
                .maxOrNull() ?: 1
            SkillDefinition(
                id = id.toString(),
                apiId = id,
                name = localizedText(skill, englishSkillsById[id], "name", "Habilidade #$id"),
                maxLevel = maxLevel.coerceAtLeast(1),
                description = localizedText(skill, englishSkillsById[id], "description", "")
            )
        }.sortedBy { it.name.lowercase(Locale.ROOT) }

        return ApiCatalog(
            skills = skillDefinitions,
            talismans = rawCharms.sortedBy { it.name },
            equipment = rawArmor.sortedWith(
                compareBy<Equipment> { equipmentSlots.indexOf(it.slot) }.thenBy { it.name }
            )
        )
    }

    private fun parseSkillBonuses(array: JSONArray?): Map<String, Int> {
        if (array == null) return emptyMap()
        val bonuses = mutableMapOf<String, Int>()
        for (i in 0 until array.length()) {
            val rank = array.optJSONObject(i) ?: continue
            val skillId = rank.optJSONObject("skill")?.optInt("id", -1) ?: -1
            val level = rank.optInt("level", 0)
            if (skillId >= 0 && level > 0) {
                val key = skillId.toString()
                bonuses[key] = (bonuses[key] ?: 0) + level
            }
        }
        return bonuses
    }
}

private fun calculateSkillTotals(
    skillDefinitions: List<SkillDefinition>,
    talisman: Talisman?,
    equipment: List<Equipment>
): Map<String, Int> {
    val totals = mutableMapOf<String, Int>()
    talisman?.bonuses?.forEach { (id, level) ->
        totals[id] = (totals[id] ?: 0) + level
    }
    equipment.forEach { item ->
        item.bonuses.forEach { (id, level) ->
            totals[id] = (totals[id] ?: 0) + level
        }
    }
    return if (skillDefinitions.isEmpty()) totals else skillDefinitions.associate { skill ->
        skill.id to (totals[skill.id] ?: 0).coerceAtMost(skill.maxLevel)
    }
}

private fun scoreBuild(
    desiredLevels: Map<String, Int>,
    achieved: Map<String, Int>
): Int {
    return desiredLevels.entries.filter { it.value > 0 }.sumOf { (skillId, target) ->
        val actual = achieved[skillId] ?: 0
        minOf(actual, target) * 100 - (actual - target).coerceAtLeast(0) * 10
    }
}

/*
 * Busca uma combinação de cinco peças e avalia todos os talismãs disponíveis,
 * incluindo a opção de não equipar nenhum. A busca é limitada a 120 candidatos
 * por etapa para evitar uma explosão no número de combinações.
 */
private fun generateBestBuild(
    skillDefinitions: List<SkillDefinition>,
    desiredLevels: Map<String, Int>,
    talismanCatalog: List<Talisman>,
    equipmentCatalog: List<Equipment>
): BuildConfiguration {
    val talismanOptions: List<Talisman?> = listOf(null) + talismanCatalog
    val optionsBySlot = equipmentSlots.map { slot ->
        equipmentCatalog.filter { it.slot == slot }
    }.filter { it.isNotEmpty() }

    var candidates = talismanOptions.map { talisman ->
        talisman to emptyList<Equipment>()
    }

    optionsBySlot.forEach { options ->
        candidates = candidates.flatMap { candidate ->
            options.map { item -> candidate.first to (candidate.second + item) }
        }.sortedByDescending { candidate ->
            scoreBuild(
                desiredLevels,
                calculateSkillTotals(skillDefinitions, candidate.first, candidate.second)
            )
        }.take(120)
    }

    val bestCandidate = candidates.maxByOrNull { candidate ->
        scoreBuild(
            desiredLevels,
            calculateSkillTotals(skillDefinitions, candidate.first, candidate.second)
        )
    }

    return BuildConfiguration(
        id = System.currentTimeMillis(),
        desiredLevels = desiredLevels,
        talisman = bestCandidate?.first,
        equipment = bestCandidate?.second.orEmpty()
    )
}

private fun saveBuilds(context: Context, builds: List<BuildConfiguration>) {
    val jsonArray = JSONArray()
    builds.forEach { build ->
        val desiredJson = JSONObject()
        build.desiredLevels.forEach { (id, level) -> desiredJson.put(id, level) }
        val equipmentJson = JSONArray()
        build.equipment.forEach { equipmentJson.put(it.id) }
        jsonArray.put(
            JSONObject().apply {
                put("id", build.id)
                put("desiredLevels", desiredJson)
                put("talismanId", build.talisman?.id ?: "")
                put("equipmentIds", equipmentJson)
            }
        )
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(SAVED_BUILDS_KEY, jsonArray.toString())
        .apply()
}

private fun loadBuilds(
    context: Context,
    skills: List<SkillDefinition>,
    talismans: List<Talisman>,
    equipmentCatalog: List<Equipment>
): List<BuildConfiguration> {
    val storedJson = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(SAVED_BUILDS_KEY, null) ?: return emptyList()
    return try {
        val jsonArray = JSONArray(storedJson)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                val desiredJson = item.optJSONObject("desiredLevels") ?: JSONObject()
                val desired = skills.associate { skill ->
                    skill.id to desiredJson.optInt(skill.id, 0).coerceIn(0, skill.maxLevel)
                }.filterValues { it > 0 }
                val talismanId = item.optString("talismanId", "")
                val talisman = talismans.firstOrNull { it.id == talismanId }
                val ids = item.optJSONArray("equipmentIds") ?: JSONArray()
                val equipment = buildList {
                    for (i in 0 until ids.length()) {
                        val id = ids.optString(i)
                        equipmentCatalog.firstOrNull { it.id == id }?.let(::add)
                    }
                }
                if (equipment.isNotEmpty() || talisman != null) {
                    add(
                        BuildConfiguration(
                            id = item.optLong("id", System.currentTimeMillis() + index),
                            desiredLevels = desired,
                            talisman = talisman,
                            equipment = equipment
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
fun BuildPlannerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf("home") }
    var catalog by remember { mutableStateOf<ApiCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var reloadToken by remember { mutableStateOf(0) }
    var selectedLevels by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var currentBuild by remember { mutableStateOf<BuildConfiguration?>(null) }
    var saveMessage by remember { mutableStateOf("") }
    val savedBuilds = remember { mutableStateListOf<BuildConfiguration>() }

    LaunchedEffect(reloadToken) {
        loading = true
        errorMessage = ""
        try {
            val loadedCatalog = withContext(Dispatchers.IO) { MhWildsApi.loadCatalog() }
            catalog = loadedCatalog
            selectedLevels = loadedCatalog.skills.associate { it.id to 0 }
            savedBuilds.clear()
            savedBuilds.addAll(
                loadBuilds(
                    context,
                    loadedCatalog.skills,
                    loadedCatalog.talismans,
                    loadedCatalog.equipment
                )
            )
            if (loadedCatalog.skills.isEmpty() || loadedCatalog.equipment.isEmpty()) {
                errorMessage = "A API respondeu, mas não retornou armaduras ou habilidades para este idioma."
            }
        } catch (error: Exception) {
            errorMessage = error.message ?: "Não foi possível carregar os dados do jogo."
            catalog = null
        } finally {
            loading = false
        }
    }

    val skills = catalog?.skills.orEmpty()
    val talismans = catalog?.talismans.orEmpty()
    val equipment = catalog?.equipment.orEmpty()

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
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

            if (loading) {
                Spacer(Modifier.height(35.dp))
                CircularProgressIndicator(color = Gold)
                Text("Carregando dados de Monster Hunter Wilds...", color = Muted)
            } else if (errorMessage.isNotBlank()) {
                Text("Não foi possível carregar o catálogo", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(errorMessage, color = Muted)
                Text("Confira sua conexão com a internet e tente novamente.", color = Muted)
                ActionButton("Tentar novamente") { reloadToken++ }
            } else {
                when (screen) {
                    "home" -> {
                        Spacer(Modifier.height(35.dp))
                        Text(
                            "Monte sua próxima configuração",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Escolha as habilidades desejadas. O aplicativo combina automaticamente armaduras e talismãs para encontrar uma configuração adequada.",
                            color = Muted,
                            fontSize = 16.sp
                        )
                        Text(
                            "${equipment.size} peças de armadura HR, ${skills.size} habilidades e ${talismans.size} talismãs carregados da API.",
                            color = Gold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        ActionButton("Montar configuração") {
                            selectedLevels = emptyMap()
                            screen = "skills"
                        }
                        ActionButton("Minhas configurações salvas (${savedBuilds.size})") { screen = "saved" }
                        Text(
                            "Os dados são carregados da MHDB API. Armas e decorações ainda não fazem parte desta etapa.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }

                    "skills" -> {
                        Text("Selecionar habilidades", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Escolha os níveis desejados. Nível 0 significa que a habilidade não foi selecionada.",
                            color = Muted
                        )
                        Text(
                            "${selectedLevels.values.count { it > 0 }} habilidades selecionadas",
                            color = Gold
                        )

                        skills.forEach { skill ->
                            val level = selectedLevels[skill.id] ?: 0
                            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(skill.name, fontWeight = FontWeight.SemiBold)
                                    if (skill.description.isNotBlank() && skill.description != "null") {
                                        Text(skill.description, color = Muted, fontSize = 12.sp)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Nível $level / ${skill.maxLevel}", color = Muted)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedLevels = selectedLevels + (
                                                            skill.id to (level - 1).coerceAtLeast(0)
                                                            )
                                                },
                                                enabled = level > 0
                                            ) { Text("−") }
                                            Spacer(Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    selectedLevels = selectedLevels + (
                                                            skill.id to (level + 1).coerceAtMost(skill.maxLevel)
                                                            )
                                                },
                                                enabled = level < skill.maxLevel
                                            ) { Text("+") }
                                        }
                                    }
                                }
                            }
                        }

                        if (saveMessage.isNotBlank()) Text(saveMessage, color = Gold)
                    }

                    "results" -> {
                        val build = currentBuild
                        Text("Configuração sugerida", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Combinação calculada usando peças de armadura High Rank e avaliação automática de talismãs.",
                            color = Gold
                        )

                        if (build == null) {
                            Text("Nenhuma configuração foi gerada. Volte e tente novamente.", color = Muted)
                        } else {
                            val achieved = calculateSkillTotals(skills, build.talisman, build.equipment)
                            Text("Habilidades desejadas e alcançadas", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            skills.filter { (build.desiredLevels[it.id] ?: 0) > 0 }.forEach { skill ->
                                val target = build.desiredLevels[skill.id] ?: 0
                                val actual = achieved[skill.id] ?: 0
                                Text(
                                    "${if (actual >= target) "✓" else "•"} ${skill.name}: $actual / $target",
                                    color = if (actual >= target) Success else Gold
                                )
                            }

                            HorizontalDivider(color = Muted)
                            Text("Armaduras selecionadas", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            build.equipment.forEach { item ->
                                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(item.slot, color = Muted)
                                        Text(item.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Raridade ${item.rarity} • Defesa máxima ${item.defense}",
                                            color = Muted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            build.talisman?.let { talisman ->
                                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text("Talismã", color = Muted)
                                        Text(talisman.name, fontWeight = FontWeight.SemiBold)
                                        val bonusText = talisman.bonuses.entries.joinToString { (id, value) ->
                                            val skillName = skills.firstOrNull { it.id == id }?.name ?: "Habilidade"
                                            "$skillName +$value"
                                        }
                                        if (bonusText.isNotBlank()) {
                                            Text(bonusText, color = Muted, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            Text(
                                "O cálculo considera bônus diretos de habilidades nas armaduras e nos talismãs. A API não fornece filtro explícito de High Rank para talismãs; bônus de conjunto e decorações ainda não entram no cálculo. A busca é aproximada e limitada aos melhores candidatos de cada etapa.",
                                color = Muted,
                                fontSize = 12.sp
                            )

                            val alreadySaved = savedBuilds.any { it.id == build.id }
                            ActionButton(if (alreadySaved) "Configuração salva" else "Salvar configuração") {
                                if (!alreadySaved) {
                                    savedBuilds.add(build)
                                    saveBuilds(context, savedBuilds)
                                    saveMessage = "Configuração salva neste dispositivo."
                                }
                            }
                            if (saveMessage.isNotBlank()) Text(saveMessage, color = Success)
                        }

                        ActionButton("Alterar habilidades") { screen = "skills" }
                        ActionButton("Voltar ao início") { screen = "home" }
                    }

                    "saved" -> {
                        Text("Configurações salvas", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "As configurações ficam armazenadas neste dispositivo, mesmo depois de fechar o aplicativo.",
                            color = Muted
                        )
                        if (savedBuilds.isEmpty()) {
                            Text("Nenhuma configuração salva ainda.", color = Muted)
                        } else {
                            savedBuilds.sortedByDescending { it.id }.forEach { build ->
                                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Configuração de ${formatDate(build.id)}",
                                            fontWeight = FontWeight.Bold
                                        )
                                        val summary = skills
                                            .filter { (build.desiredLevels[it.id] ?: 0) > 0 }
                                            .joinToString { skill ->
                                                "${skill.name} ${build.desiredLevels[skill.id]}"
                                            }
                                        Text(summary.ifBlank { "Sem habilidades selecionadas" }, color = Muted)
                                        Text(
                                            "Talismã: ${build.talisman?.name ?: "Nenhum"}",
                                            color = Muted,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "Armaduras: ${build.equipment.joinToString { it.name }}",
                                            color = Muted,
                                            fontSize = 12.sp
                                        )
                                        TextButton(
                                            onClick = {
                                                savedBuilds.remove(build)
                                                saveBuilds(context, savedBuilds)
                                            }
                                        ) {
                                            Text("Excluir configuração", color = Gold)
                                        }
                                    }
                                }
                            }
                        }
                        ActionButton("Montar nova configuração") {
                            selectedLevels = emptyMap()
                            screen = "skills"
                        }
                    }
                }
            }
        }

        if (!loading && errorMessage.isBlank() && screen != "home") {
            HorizontalDivider(color = SurfaceDark)
            TextButton(
                onClick = {
                    screen = when (screen) {
                        "skills" -> "home"
                        "results" -> "skills"
                        else -> "home"
                    }
                },
                modifier = Modifier.padding(start = 12.dp),
                enabled = !isGenerating
            ) {
                Text("← Voltar", color = Gold)
            }
        }

        if (!loading && errorMessage.isBlank() && screen == "skills") {
            Surface(
                color = Background,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isGenerating) {
                        LoadingBuildCard()
                    } else {
                        Button(
                            onClick = {
                                isGenerating = true
                                saveMessage = ""
                                scope.launch {
                                    try {
                                        val generatedBuild = withContext(Dispatchers.Default) {
                                            generateBestBuild(
                                                skills,
                                                selectedLevels.filterValues { it > 0 },
                                                talismans,
                                                equipment
                                            )
                                        }
                                        currentBuild = generatedBuild
                                        screen = "results"
                                    } catch (error: Exception) {
                                        saveMessage = error.message
                                            ?: "Não foi possível gerar a configuração."
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            },
                            enabled = selectedLevels.values.any { it > 0 } && equipment.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = Color(0xFF17130D)
                            )
                        ) {
                            Text("Gerar build automaticamente", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun LoadingBuildCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = Gold, strokeWidth = 4.dp)
            Text(
                "Preparando sua build...",
                color = Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Analisando armaduras e talismãs para encontrar a melhor combinação.",
                color = Muted,
                fontSize = 14.sp
            )
            Text("Aguarde um instante...", color = Muted, fontSize = 12.sp)
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color(0xFF17130D)
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
