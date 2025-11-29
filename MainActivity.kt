
package com.aslihanmetehan.trivia

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import java.nio.charset.Charset
import android.content.Context

data class Question(val q: String, val options: List<String>, val answerIndex: Int)

class TriviaViewModel(context: Context): ViewModel() {
    private val assets = context.assets
    var questions by mutableStateOf(listOf<Question>())
        private set
    var index by mutableStateOf(0)
    var score by mutableStateOf(0)
    var timeLeft by mutableStateOf(15)
    var finished by mutableStateOf(false)
    var selected by mutableStateOf(-1)
    var highScore by mutableStateOf(0)

    init {
        loadQuestions()
        loadHighScore(context)
    }

    private fun loadQuestions() {
        try {
            val jsonStr = assets.open("questions.json").readBytes().toString(Charset.defaultCharset())
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<Question>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val q = obj.getString("q")
                val opts = obj.getJSONArray("options")
                val options = mutableListOf<String>()
                for (j in 0 until opts.length()) options.add(opts.getString(j))
                val ans = obj.getInt("answer")
                list.add(Question(q, options, ans))
            }
            questions = list
        } catch (e: Exception) {
            questions = listOf(Question("Soru yüklenemedi", listOf("—","—","—"), 0))
        }
    }

    private fun loadHighScore(context: Context){
        val prefs = context.getSharedPreferences("trivia", Context.MODE_PRIVATE)
        highScore = prefs.getInt("highscore", 0)
    }

    private fun saveHighScore(context: Context){
        val prefs = context.getSharedPreferences("trivia", Context.MODE_PRIVATE)
        prefs.edit().putInt("highscore", highScore).apply()
    }

    fun selectOption(context: Context, idx: Int){
        if (selected != -1) return
        selected = idx
        if (idx == currentQuestion().answerIndex) {
            score += 10
            if (score > highScore) {
                highScore = score
                saveHighScore(context)
            }
        }
    }

    fun nextQuestion() {
        selected = -1
        timeLeft = 15
        index += 1
        if (index >= questions.size) finished = true
    }

    fun restart(context: Context){
        index = 0; score = 0; timeLeft = 15; finished = false; selected = -1
        loadHighScore(context)
    }

    fun currentQuestion(): Question {
        return questions.getOrElse(index) { Question("Bitti", listOf("—","—","—"), 0) }
    }
}

class MainActivity : ComponentActivity() {
    private var soundPool: SoundPool? = null
    private var soundCorrect = 0
    private var soundWrong = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // init SoundPool
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        soundCorrect = soundPool!!.load(assets.openFd("correct.wav"), 1)
        soundWrong = soundPool!!.load(assets.openFd("wrong.wav"), 1)

        setContent {
            var showSplash by remember { mutableStateOf(true) }
            val vm: TriviaViewModel = viewModel(factory = object: androidx.lifecycle.ViewModelProvider.Factory{
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return TriviaViewModel(this@MainActivity) as T
                }
            })

            if (showSplash) {
                SplashScreen {
                    showSplash = false
                }
            } else {
                TriviaScreen(vm = vm, playCorrect = { playSound(true) }, playWrong = { playSound(false) })
            }
        }
    }

    private fun playSound(correct: Boolean) {
        soundPool?.let {
            it.play(if (correct) soundCorrect else soundWrong, 1f, 1f, 1, 0, 1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
        soundPool = null
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1800)
        onFinished()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.primaryVariant) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Aslıhan ve Metehan", fontSize = 28.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Trivia", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun TriviaScreen(vm: TriviaViewModel, playCorrect: ()->Unit, playWrong: ()->Unit) {
    val ctx = LocalContext.current
    val q = vm.currentQuestion()
    LaunchedEffect(vm.index) {
        vm.timeLeft = 15
        while (vm.timeLeft > 0 && !vm.finished && vm.selected == -1) {
            kotlinx.coroutines.delay(1000)
            vm.timeLeft -= 1
        }
        if (vm.timeLeft == 0 && !vm.finished && vm.selected == -1) {
            vm.nextQuestion()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Aslıhan ve Metehan Trivia") }) }) { padding ->
        if (vm.finished) {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Oyun Bitti", fontSize = 28.sp)
                Spacer(Modifier.height(8.dp))
                Text("Puan: ${'$'}{vm.score}")
                Text("En yüksek: ${'$'}{vm.highScore}")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.restart(ctx) }) { Text("Yeniden oyna") }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Soru ${'$'}{vm.index + 1}/${'$'}{vm.questions.size}")
                    Text("Süre: ${'$'}{vm.timeLeft}s")
                }
                Spacer(Modifier.height(12.dp))
                Text(q.q, fontSize = 20.sp)
                Spacer(Modifier.height(12.dp))
                q.options.forEachIndexed { i, opt ->
                    val enabled = vm.selected == -1
                    OutlinedButton(onClick = {
                        vm.selectOption(ctx, i)
                        if (i == vm.currentQuestion().answerIndex) playCorrect() else playWrong()
                    }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), enabled = enabled) {
                        Text(opt)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Button(onClick = { vm.nextQuestion() }, enabled = vm.selected != -1) { Text("İleri") }
                    Spacer(Modifier.width(8.dp))
                    Text("Puan: ${'$'}{vm.score}")
                }
            }
        }
    }
}
