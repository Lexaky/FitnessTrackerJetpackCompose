package com.fefu.fitnesstracker
import android.annotation.*
import android.content.*
import android.os.Bundle
import android.util.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.material.icons.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.room.*
import com.fefu.fitnesstracker.ui.theme.FitnessTrackerTheme
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.*
import kotlinx.coroutines.*
import org.joda.time.*
import java.net.*

import androidx.room.Database
import androidx.room.Entity
import androidx.room.RoomDatabase

// Перечисление типов активности
enum class ActivityType {
    Велосипед, Бег, Шаг
}

// Класс для координат
data class Coordinate(
    val latitude: Double,
    val longitude: Double
)

// Конвертер для списка координат
class Converters {
    @TypeConverter
    fun fromCoordinates(coordinates: List<Coordinate>?): String? {
        return Gson().toJson(coordinates)
    }

    @TypeConverter
    fun toCoordinates(coordinatesString: String?): List<Coordinate>? {
        return Gson().fromJson(coordinatesString, object : TypeToken<List<Coordinate>>() {}.type)
    }

    @TypeConverter
    fun fromDateTime(dateTime: DateTime?): Long? {
        return dateTime?.millis
    }

    @TypeConverter
    fun toDateTime(millis: Long?): DateTime? {
        return millis?.let { DateTime(it) }
    }
}

// Сущность для таблицы
@Entity(tableName = "activities")
@TypeConverters(Converters::class)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val activityType: ActivityType,
    val startTime: DateTime,
    val endTime: DateTime?,
    val coordinates: List<Coordinate>?
) {
    // Метод для вычисления расстояния (примерная реализация, требует уточнения)
    fun calculateDistance(): Double {
        if (coordinates.isNullOrEmpty() || coordinates.size < 2) return 0.0
        var totalDistance = 0.0
        for (i in 0 until coordinates.size - 1) {
            val lat1 = Math.toRadians(coordinates[i].latitude)
            val lon1 = Math.toRadians(coordinates[i].longitude)
            val lat2 = Math.toRadians(coordinates[i + 1].latitude)
            val lon2 = Math.toRadians(coordinates[i + 1].longitude)

            val dLat = lat2 - lat1
            val dLon = lon2 - lon1
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            totalDistance += 6371 * c // Радиус Земли в км
        }
        return totalDistance
    }
}

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE endTime IS NOT NULL")
    fun getAllActivities(): LiveData<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveActivity(): ActivityEntity?

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getActivityById(id: Int): ActivityEntity?
}

@Database(entities = [ActivityEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitness_tracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitnessTrackerTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") { MainScreen(navController) }
                    composable("second") { RegistrationScreen(navController) }
                    composable("third") { LoginScreen(navController) }
                    composable("fourd") { ActivityListScreen(navController) }
                    composable(
                        route = "activity_detail/{activityId}",
                        arguments = listOf(
                            navArgument("activityId") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        val activityId = backStackEntry.arguments?.getInt("activityId") ?: -1
                        ActivityDetailScreen(navController = navController, activityId = activityId)
                    }
                    composable("change_password") { ChangePasswordScreen(navController) }
                    composable("start_activity") { StartActivityScreen(navController) }
                }
            }
        }
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FitnessTrackerTheme {
        Greeting("Android")
    }
}

@Composable
fun MainScreen(navController: NavController) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Пожалуй лучший фитнес-трекер в ДВФУ",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "созданный студентами 2-го курса",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Button(
                onClick = { navController.navigate("second") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EE), // Фиолетово-синий
                    contentColor = Color.White // Белый текст
                ),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Зарегистрироваться")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { navController.navigate("third") },
                border = BorderStroke(1.dp, Color(0xFF6200EE)), // Фиолетово-синяя рамка
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF6200EE), // Цвет текста
                    containerColor = Color.Transparent // Прозрачный фон
                ),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Уже есть аккаунт?")
            }
        }
    }
}


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(navController: NavController) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Мужской", "Женский", "Другое")
    var selectedGender by remember { mutableStateOf(options[0]) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Регистрация") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Логин
        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text("Логин") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Пароль
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle Password Visibility"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Повторите пароль
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Повторите пароль") },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle Password Visibility"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Имя или никнейм
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Имя или никнейм") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Пол
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedGender)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedGender = option
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка "Продолжить"
        Button(
            // ИЗМЕНИТЬ, ВХОД ДОЛЖЕН АУТЕНТИФИЦИРОВАТЬСЯ
            onClick = { navController.navigate("fourd") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Продолжить", color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Политика конфиденциальности
        Text(
            text = "Нажимая на кнопку, вы соглашаетесь с политикой конфиденциальности и обработки персональных данных, а также принимаете пользовательское соглашение",
            fontSize = 12.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вход") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Поле логина
            OutlinedTextField(
                value = login,
                onValueChange = { login = it },
                label = { Text("Логин") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Поле пароля
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Показать пароль"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка "Продолжить"
            Button(
                onClick = { navController.navigate("fourd") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)), // Фиолетово-синий цвет
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Продолжить", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun ActivityListScreen(navController: NavController) {
    val context = LocalContext.current
    val activityDao = remember { AppDatabase.getDatabase(context).activityDao() }
    val activitiesLiveData = activityDao.getAllActivities()
    val activities by activitiesLiveData.observeAsState(emptyList())

    var selectedTopTab by remember { mutableIntStateOf(0) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }
    val topTabs = listOf("Моя", "Пользователей")
    val bottomTabs = listOf("Активность", "Профиль")

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedBottomTab == index,
                        onClick = { selectedBottomTab = index },
                        icon = {
                            Icon(
                                imageVector = if (index == 0) Icons.Default.List else Icons.Default.Person,
                                contentDescription = title
                            )
                        },
                        label = { Text(title) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("start_activity") },
                modifier = Modifier.padding(16.dp),
                containerColor = Color(0xFF6200EE),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Начать активность"
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTopTab) {
                topTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTopTab == index,
                        onClick = { selectedTopTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedBottomTab) {
                0 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (selectedTopTab == 0) {
                            items(activities) { activity ->
                                ActivityCardFromDb(activity, navController)
                            }
                        } else {
                            items(getMockActivitiesUsers()) { item ->
                                when (item) {
                                    is ActivityItem.Section -> SectionHeader(item.date)
                                    is ActivityItem.Activity -> ActivityCard(item, navController)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    ProfileScreen(navController)
                }
            }
        }
    }
}

@Composable
fun ActivityCardFromDb(activity: ActivityEntity, navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                val route = "activity_detail/${activity.id}"
                Log.d("NavigationDebug", "Attempting to navigate to: $route")
                try {
                    navController.navigate(route)
                    Log.d("NavigationDebug", "Navigation successful")
                } catch (e: Exception) {
                    Log.e("NavigationDebug", "Navigation failed with exception: ${e.message}", e)
                    e.printStackTrace()
                }
            },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = String.format("%.2f км", activity.calculateDistance()),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${activity.endTime?.millis?.minus(activity.startTime.millis)?.div(60000) ?: 0} минут",
                fontSize = 16.sp
            )
            Text(text = activity.activityType.name, fontSize = 16.sp, fontStyle = FontStyle.Italic)
            Text(text = "@user", fontSize = 14.sp, color = Color.Blue)
            Text(text = activity.startTime.toString("dd.MM.yyyy HH:mm"), fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartActivityScreen(navController: NavController) {
    val context = LocalContext.current
    val activityDao = remember { AppDatabase.getDatabase(context).activityDao() }
    var selectedActivity by remember { mutableStateOf<ActivityType?>(null) }
    var isActivityStarted by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableLongStateOf(0L) }
    var startTime by remember { mutableStateOf<DateTime?>(null) }
    var currentActivityId by remember { mutableStateOf<Int?>(null) } // Для хранения ID активной записи

    LaunchedEffect(isActivityStarted, isPaused) {
        if (isActivityStarted && !isPaused) {
            while (true) {
                delay(1000L)
                elapsedTime += 1000L
            }
        }
    }

    val formattedTime = remember(elapsedTime) {
        val seconds = (elapsedTime / 1000) % 60
        val minutes = (elapsedTime / 1000 / 60) % 60
        val hours = (elapsedTime / 1000 / 60 / 60)
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Начать активность") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("fourd") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.LightGray)
            ) {
                Text(
                    text = "Место для Google Maps",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isActivityStarted) {
                        Text(
                            text = "Погнали? :)",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val activities = listOf(ActivityType.Велосипед, ActivityType.Бег, ActivityType.Шаг)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(activities) { activity ->
                                Card(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clickable { selectedActivity = activity },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedActivity == activity) Color(0xFF6200EE) else Color.Gray
                                    )
                                ) {
                                    Text(
                                        text = activity.name,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .align(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (selectedActivity != null) {
                                    isActivityStarted = true
                                    startTime = DateTime.now()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val entity = ActivityEntity(
                                            activityType = selectedActivity!!,
                                            startTime = startTime!!,
                                            endTime = null,
                                            coordinates = listOf(
                                                Coordinate(55.7558, 37.6173),
                                                Coordinate(55.7580, 37.6200)
                                            )
                                        )
                                        val id = activityDao.insert(entity) // Room возвращает ID вставленной записи
                                        currentActivityId = id.toInt()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = selectedActivity != null
                        ) {
                            Text(text = "Начать", color = Color.White, fontSize = 16.sp)
                        }
                    } else {
                        Text(
                            text = selectedActivity?.name ?: "Неизвестно",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "0 км",
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                            Text(
                                text = formattedTime,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FloatingActionButton(
                                onClick = { isPaused = !isPaused },
                                containerColor = Color(0xFF6200EE),
                                contentColor = Color.White,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = if (isPaused) "Продолжить" else "Пауза"
                                )
                            }
                            FloatingActionButton(
                                onClick = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val activeActivity = activityDao.getActiveActivity()
                                        if (activeActivity != null) {
                                            activityDao.update(
                                                activeActivity.copy(
                                                    endTime = DateTime.now()
                                                )
                                            )
                                        }
                                    }
                                    isActivityStarted = false
                                    selectedActivity = null
                                    elapsedTime = 0L
                                    isPaused = false
                                    currentActivityId = null
                                    navController.navigate("fourd")
                                },
                                containerColor = Color.Red,
                                contentColor = Color.White,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Завершить"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    var login by remember { mutableStateOf("user123") } // Захардкодленный логин
    var nickname by remember { mutableStateOf("John Doe") } // Захардкодленный никнейм

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("fourd") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    Text(
                        text = "Сохранить",
                        color = Color(0xFF6200EE), // Фиолетовый цвет
                        fontSize = 16.sp,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable { /* Пока ничего не делает */ }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Имя или никнейм") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Изменить пароль",
                    color = Color(0xFF6200EE), // Фиолетовый цвет
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable { navController.navigate("change_password") }
                )
            }
            Button(
                onClick = { /* Логика выхода */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(text = "Выйти", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(navController: NavController) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Изменить пароль") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("fourd") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                label = { Text("Старый пароль") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation() // Скрытие пароля
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Новый пароль") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = confirmNewPassword,
                onValueChange = { confirmNewPassword = it },
                label = { Text("Повторите новый пароль") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { /* Логика принятия пароля */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(text = "Принять", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

fun navigateToActivityDetail(navController: NavController, activity: ActivityItem.Activity) {
    try {
        val encodedDistance = URLEncoder.encode(activity.distance, "UTF-8")
        val encodedUnit = URLEncoder.encode(activity.unit, "UTF-8")
        val encodedDuration = URLEncoder.encode(activity.duration, "UTF-8")
        val encodedType = URLEncoder.encode(activity.type, "UTF-8")
        val encodedTimeAgo = URLEncoder.encode(activity.timeAgo, "UTF-8")
        val encodedUserTag = URLEncoder.encode(activity.userTag, "UTF-8")
        val encodedStartTime = URLEncoder.encode(activity.startTime, "UTF-8")
        val encodedEndTime = URLEncoder.encode(activity.endTime, "UTF-8")
        val encodedComment = URLEncoder.encode(activity.comment, "UTF-8")

        val route = "activity_detail/$encodedDistance/$encodedUnit/$encodedDuration/$encodedType/$encodedTimeAgo/" +
                "$encodedUserTag/$encodedStartTime/$encodedEndTime/$encodedComment"
        navController.navigate(route)
    } catch (e: IllegalArgumentException) {
        Log.e("NavigationError", "Failed to navigate: ${e.message}")
    }
}

// Заголовок секции (дата)
@Composable
fun SectionHeader(date: String) {
    Text(
        text = date,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun ActivityCard(activity: ActivityItem.Activity, navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                // Используем временный ID -1 для моковых данных
                val route = "activity_detail/-1"
                Log.d("NavigationDebug", "Navigating to mock activity: $route")
                navController.navigate(route)
            },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${activity.distance} ${activity.unit}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = "${activity.duration} минут", fontSize = 16.sp)
            Text(text = activity.type, fontSize = 16.sp, fontStyle = FontStyle.Italic)
            Text(text = "Автор: ${activity.userTag}", fontSize = 14.sp, color = Color.Blue)
            Text(text = activity.timeAgo, fontSize = 14.sp, color = Color.Gray)
        }
    }
}




sealed class ActivityItem {
    data class Section(val date: String) : ActivityItem()
    data class Activity(
        val distance: String,
        val unit: String,
        val duration: String,
        val type: String,
        val timeAgo: String,
        val userTag: String,         // Тег пользователя, например "@john_doe"
        val startTime: String,       // Время начала, например "10:00"
        val endTime: String,         // Время окончания, например "11:30"
        val comment: String = ""     // Комментарий, по умолчанию пустой
    ) : ActivityItem()
}

fun getMockActivitiesMy(): List<ActivityItem> {
    return listOf(
        ActivityItem.Section("Вчера"),
        ActivityItem.Activity(
            distance = "14.32",
            unit = "км",
            duration = "166",
            type = "Серфинг",
            timeAgo = "14 часов назад",
            userTag = "@my_name",
            startTime = "09:00",
            endTime = "11:46",
            comment = "Отличная погода!"
        ),
        ActivityItem.Section("Май 2022 года"),
        ActivityItem.Activity(
            distance = "1000",
            unit = "м",
            duration = "60",
            type = "Велосипед",
            timeAgo = "29.05.2022",
            userTag = "@my_name",
            startTime = "14:00",
            endTime = "15:00",
            comment = ""
        )
    )
}

fun getMockActivitiesUsers(): List<ActivityItem> {
    return listOf(
        ActivityItem.Section("Сегодня"),
        ActivityItem.Activity(
            distance = "5.5",
            unit = "км",
            duration = "45",
            type = "Бег",
            timeAgo = "2 часа назад",
            userTag = "@runner_guy",
            startTime = "07:00",
            endTime = "07:45",
            comment = "Утренний забег"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(navController: NavController, activityId: Int) {
    val context = LocalContext.current
    val activityDao = remember { AppDatabase.getDatabase(context).activityDao() }
    var activity by remember { mutableStateOf<ActivityEntity?>(null) }

    LaunchedEffect(activityId) {
        if (activityId != -1) { // Реальные данные из БД
            activity = withContext(Dispatchers.IO) {
                activityDao.getActivityById(activityId)
            }
        }
    }

    if (activityId == -1) { // Моковые данные
        val mockActivity = ActivityItem.Activity(
            distance = "5.5",
            unit = "км",
            duration = "45",
            type = "Бег",
            timeAgo = "2 часа назад",
            userTag = "@runner_guy",
            startTime = "07:00",
            endTime = "07:45",
            comment = "Утренний забег"
        )
        var commentText by remember { mutableStateOf(mockActivity.comment) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(mockActivity.type) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Логика удаления */ }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                        IconButton(onClick = { /* Логика поделиться */ }) {
                            Icon(Icons.Default.Share, contentDescription = "Поделиться")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                Text(text = "${mockActivity.distance} ${mockActivity.unit}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "${mockActivity.duration} минут", fontSize = 20.sp)
                Text(text = "Автор: ${mockActivity.userTag}", fontSize = 18.sp, color = Color.Blue)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Начало: ${mockActivity.startTime}", fontSize = 16.sp)
                Text(text = "Окончание: ${mockActivity.endTime}", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = mockActivity.timeAgo, fontSize = 16.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        activity?.let { act ->
            var commentText by remember { mutableStateOf("") }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(act.activityType.name) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        },
                        actions = {
                            IconButton(onClick = { /* Логика удаления */ }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                            IconButton(onClick = { /* Логика поделиться */ }) {
                                Icon(Icons.Default.Share, contentDescription = "Поделиться")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(text = "${String.format("%.2f", act.calculateDistance())} км", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "${act.endTime?.millis?.minus(act.startTime.millis)?.div(60000) ?: 0} минут", fontSize = 20.sp)
                    Text(text = "Автор: @user", fontSize = 18.sp, color = Color.Blue)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Начало: ${act.startTime.toString("HH:mm")}", fontSize = 16.sp)
                    Text(text = "Окончание: ${act.endTime?.toString("HH:mm") ?: "N/A"}", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = act.startTime.toString("dd.MM.yyyy HH:mm"), fontSize = 16.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        label = { Text("Комментарий") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

