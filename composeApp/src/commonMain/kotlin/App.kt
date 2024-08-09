import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateIntSizeAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key.Companion.R
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.baseline_file_download_24
import kotlinproject.composeapp.generated.resources.baseline_file_download_off_24
import kotlinproject.composeapp.generated.resources.baseline_file_upload_24
import kotlinproject.composeapp.generated.resources.baseline_file_upload_off_24
import kotlinproject.composeapp.generated.resources.baseline_sync_24
import kotlinproject.composeapp.generated.resources.baseline_sync_disabled_24
import kotlinproject.composeapp.generated.resources.baseline_sync_problem_24
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt


enum class ShownView {
    CHARACTERS,
    TURNS
}

data class ViewState(
    val shownView: ShownView,
    val currentlyEditedCharacter: String?
)


@Composable
fun ShowCharacter(character: Character, isActive: Boolean, actions: Actions, viewState: ViewState, isGreyed: Boolean = false, toggleEditCharacter: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }

    var modifier: Modifier = Modifier.fillMaxWidth()
    if (viewState.shownView == ShownView.TURNS) {
        val isActiveAlpha by animateFloatAsState(if (isActive) 1f else 0f)
        modifier = modifier.then(Modifier.background(color = Color.Yellow.copy(alpha = isActiveAlpha)))
    }
    modifier =
        modifier.then(Modifier.padding(vertical = 10.dp, horizontal = 20.dp).heightIn(min = 60.dp))

    val focusManager = LocalFocusManager.current

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(30.dp)) {
            if (viewState.shownView == ShownView.TURNS && !character.dead) {
                Text("${character.turn + 1}", )
            }
        }
        if (!character.dead) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                if (viewState.currentlyEditedCharacter != character.key) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(character.name ?: "")
                        ShowPlayerVsNonPlayerCharacter(viewState, character, actions)
                        if (character.isDelayed && viewState.shownView == ShownView.TURNS) {
                            Text(
                                "Delayed",
                                Modifier.padding(horizontal = 10.dp),
                                fontStyle = FontStyle.Italic
                            )
                            OutlinedButton(onClick = { actions.startTurn(character.key) }) {
                                Text("Take")
                            }
                        }
                    }
                } else {
                    TextField(
                        modifier = if (character.name == null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                        singleLine = true,
                        value = character.name ?: "",
                        onValueChange = { actions.editCharacter(character.key, it) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                toggleEditCharacter(character.key)
                            },
                            onNext = {
                                focusManager.moveFocus(FocusDirection.Next)
                            }
                        ),
                        label = { Text("Name") })
                    if (character.name == null || character.initiative == null) {
                        DisposableEffect(Unit) {
                            focusRequester.requestFocus()
                            onDispose { }
                        }
                    }
                }
            }
            val toggleEditIcon = if (viewState.currentlyEditedCharacter != character.key) {
                Icons.Default.Edit
            } else {
                Icons.Default.Check
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (viewState.currentlyEditedCharacter != character.key) {
                        Text(
                            modifier = Modifier.padding(horizontal = 5.dp),
                            text = character.initiative?.toString() ?: ""
                        )
                    } else {
                        TextField(
                            modifier = Modifier.width(70.dp)
                                .padding(horizontal = 5.dp)
                                .then(
                                    if (character.initiative == null && character.name != null) Modifier.focusRequester(
                                        focusRequester
                                    ) else Modifier
                                ),
                            singleLine = true,
                            value = character.initiative?.toString() ?: "",
                            onValueChange = { actions.editInitiative(character.key, it) },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Decimal
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    toggleEditCharacter(character.key)
                                },
                                onPrevious = {
                                    focusManager.moveFocus(FocusDirection.Previous)
                                }
                            ),
                            label = { Text("In") })
                    }
                    if (viewState.shownView == ShownView.CHARACTERS) {
                        IconButton(onClick = { toggleEditCharacter(character.key) }) {
                            AnimatedContent(targetState = toggleEditIcon) {
                                Icon(it, contentDescription = "Toggle Edit")
                            }
                        }
                        IconButton(onClick = { actions.deleteCharacter(character.key) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete"
                            )
                        }
                    } else {
                        if (character.playerCharacter == true) {
                            IconButton(enabled = !isGreyed, onClick = { actions.die(character.key) }) {
                                Icon(
                                    imageVector = deathIcon,
                                    tint = Color.Black,
                                    contentDescription = "Get dying condition"
                                )
                            }
                        } else {
                            IconButton(enabled = !isGreyed, onClick = { actions.deleteCharacter(character.key) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ShowPlayerVsNonPlayerCharacter(viewState: ViewState, character: Character, actions: Actions) {
    val isPlayerCharacter = character.playerCharacter == true
    if (viewState.shownView == ShownView.CHARACTERS) {
        Button(modifier = Modifier.padding(start = 10.dp), onClick = { actions.togglePlayerCharacter(character.key, !isPlayerCharacter) }) {
            Text(text = if (isPlayerCharacter) "PC" else "NPC")
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListCharacters(
    columnScope: ColumnScope,
    characters: List<Character>,
    actions: Actions,
    listState: LazyListState,
    currentlyEditedCharacter: String?,
    toggleEditCharacter: (String) -> Unit) {
    LazyColumn(state = listState, modifier = with(columnScope) { Modifier.fillMaxWidth().weight(1f) }) {
        items(characters, key = { it.key }) { character ->
            Box(modifier = Modifier.animateItemPlacement()) {
                ShowCharacter(
                    character,
                    isActive = false,
                    actions,
                    ViewState(ShownView.CHARACTERS, currentlyEditedCharacter),
                    false,
                    toggleEditCharacter
                )
            }
        }
        item(key = "") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FloatingActionButton(modifier = Modifier.padding(top = 4.dp), onClick = { actions.addCharacter() }) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        //tint = Color.Green,
                        modifier = Modifier.size(40.dp),
                        contentDescription = "Add character"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListTurns(columnScope: ColumnScope, characters: List<Character>, active: String?, actions: Actions) {
    SubcomposeLayout(modifier = with(columnScope) {
        Modifier.fillMaxWidth().weight(1f).clipToBounds()
    }) { constraints ->
        if (characters.isEmpty()) {
            return@SubcomposeLayout layout(0, 0) {}
        }

        var currentAddTurn = -1
        var currentIndex = characters.size - 1
        var currentHeight = 0

        var oneRoundHeight = 0

        val placeables = mutableListOf<Pair<Int, Placeable>>()

        while (currentHeight < constraints.maxHeight) {
            val currentCharacter = characters[currentIndex]
            val currentTurn = currentCharacter.turn + currentAddTurn
            val slotKey = currentCharacter.key + "-" + currentTurn

            val alpha = if (currentAddTurn <= 0) 1.0f else {
                0.5f - 0.5f * (currentHeight - oneRoundHeight) / (constraints.maxHeight - oneRoundHeight)
            }

            val currentAddTurnCopy = currentAddTurn
            val s = subcompose(slotId = slotKey) {
                val actualAlpha by animateFloatAsState(alpha, finishedListener = { Napier.i("animation for $slotKey finished at $it (goal: $alpha)") })

                var targetOffset by remember { mutableStateOf(0) }
                var anim by remember { mutableStateOf<Animatable<Int, AnimationVector1D>?>(null) }
                val scope = rememberCoroutineScope()

                Box(modifier = Modifier.onPlaced { layoutCoordinates ->
                    targetOffset = layoutCoordinates.positionInParent().y.roundToInt()
                }.offset {
                    val animatable = anim ?: Animatable(targetOffset, Int.VectorConverter)
                        .also { anim = it }
                    if (animatable.targetValue != targetOffset) {
                        scope.launch { animatable.animateTo(targetOffset) }
                    }
                    IntOffset(0, animatable.value - targetOffset)
                }) {
                     Box(modifier = Modifier.alpha(actualAlpha)) {
                         val isActive = active == currentCharacter.key && currentAddTurnCopy == 0 && anim?.isRunning != true
                        ShowCharacter(
                            currentCharacter.copy(turn = currentTurn, isDelayed = currentCharacter.isDelayed && currentAddTurnCopy == 0),
                            isActive = isActive,
                            actions,
                            ViewState(ShownView.TURNS, null),
                            isGreyed = currentAddTurnCopy >= 1, {},
                        )
                    }
                }
            }
            val measured = s[0].measure(
                constraints = Constraints(
                    minWidth = constraints.minWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight - currentHeight
                )
            )
            if (currentAddTurn == -1) {
                currentHeight = -measured.height
            }
            placeables.add(currentHeight to measured)
            currentHeight += measured.height
            currentIndex++
            if (currentIndex == characters.size) {
                if (currentAddTurn == 0) {
                    oneRoundHeight = currentHeight
                }
                currentAddTurn++
                currentIndex = 0
            }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach {
                it.second.placeRelative(0, it.first)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun InitOrder(columnScope: ColumnScope, characters: List<Character>, active: String?, actions: Actions, listState: LazyListState, viewState: ViewState, toggleEditCharacter: (String) -> Unit) {
    if (viewState.shownView == ShownView.CHARACTERS) {
        ListCharacters(columnScope, characters, actions, listState, viewState.currentlyEditedCharacter, toggleEditCharacter)
    } else {
        ListTurns(columnScope, characters, active, actions)
    }
}

val deathIcon: ImageVector =
materialIcon(name = "death") {
    materialPath {
        moveTo(11.0f, 2.0f)
        verticalLineTo(7.0f)
        horizontalLineTo(6.0f)
        verticalLineToRelative(2.0f)
        horizontalLineTo(11.0f)
        verticalLineTo(22.0f)
        horizontalLineToRelative(2.0f)
        verticalLineTo(9.0f)
        horizontalLineTo(18.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineTo(13.0f)
        verticalLineTo(2.0f)
        close()
    }
}

enum class Screens(val title: String) {
    MainScreen("Initiative Tracker"),
    ListActions("List Actions"),
    ConnectionSettings("Connection Settings"),
    Parties("Parties")
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@Preview
fun App(data: String? = null) {
    val globalCoroutineScope = rememberCoroutineScope()
    val model = viewModel { Model(data) }
    LaunchedEffect(Unit) {
        val predefinedServerHost = data?.split("&")?.firstOrNull { it.startsWith("server=") }?.let {
            it.substring(7)
        }
        if (predefinedServerHost != null) {
            ClientConsumer.changeHost(predefinedServerHost)
            ClientConsumer.start(model, globalCoroutineScope)
        }
    }
    val state by model.state.collectAsState(State())

    val partiesStateFlow = remember { MutableStateFlow(mapOf<String, List<SimpleCharacter>>()) }
    val partiesState = remember { mutableStateOf(mapOf<String, List<SimpleCharacter>>()) }
    var parties by partiesState

    LaunchedEffect(parties) {
        partiesStateFlow.value = parties
    }

    LaunchedEffect(Unit) {
        val settings = Settings()

        parties = settings.getString("parties", "").split(";").mapNotNull {
            val parts = it.split(",")
            val partyName = parts.firstOrNull()
            val characters = parts.drop(1).map {
                SimpleCharacter(0, it, true)
            }
            if (partyName != null && partyName != "") {
                partyName to characters
            } else {
                null
            }
        }.toMap()

        partiesStateFlow.collect {
            settings.putString("parties", it.entries.joinToString(";") { it.key + "," + it.value.joinToString(",") { it.name } })
        }
    }

    MaterialTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()


        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val viewStateVar = remember { mutableStateOf(ViewState(ShownView.CHARACTERS, null)) }
        var viewState by viewStateVar
        val pagerState = rememberPagerState(initialPage = viewState.shownView.ordinal) { ShownView.entries.size }

        LaunchedEffect(pagerState.currentPage) {
            pagerState.interactionSource
            viewState = viewState.copy(shownView = ShownView.entries[pagerState.currentPage])
        }
        LaunchedEffect(viewState.shownView) {
            pagerState.animateScrollToPage(viewState.shownView.ordinal)
        }

        val snackBarHostState = remember { SnackbarHostState() }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text("Initiative Tracker", Modifier.padding(16.dp))
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text("Connection Settings") },
                        badge = { ConnectionState() },
                        selected = backStackEntry?.destination?.route == Screens.ConnectionSettings.name,
                        onClick = {
                            navController.navigate(Screens.ConnectionSettings.name) {
                                popUpTo(Screens.MainScreen.name)
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("Characters") },
                        selected = backStackEntry?.destination?.route == Screens.MainScreen.name && viewState.shownView == ShownView.CHARACTERS,
                        onClick = {
                            navController.navigate(Screens.MainScreen.name) {
                                popUpTo(Screens.MainScreen.name)
                                launchSingleTop = true
                            }
                            viewState = viewState.copy(shownView = ShownView.CHARACTERS)
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("Turns") },
                        selected = backStackEntry?.destination?.route == Screens.MainScreen.name && viewState.shownView == ShownView.TURNS,
                        onClick = {
                            navController.navigate(Screens.MainScreen.name) {
                                popUpTo(Screens.MainScreen.name)
                                launchSingleTop = true
                            }
                            viewState = viewState.copy(shownView = ShownView.TURNS)
                            scope.launch { drawerState.close() }
                        }
                    )
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text("List Actions") },
                        selected = backStackEntry?.destination?.route == Screens.ListActions.name,
                        onClick = {
                            navController.navigate(Screens.ListActions.name) {
                                popUpTo(Screens.MainScreen.name)
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("Parties")},
                        selected = backStackEntry?.destination?.route == Screens.Parties.name,
                        onClick = {
                            navController.navigate(Screens.Parties.name) {
                                popUpTo(Screens.MainScreen.name)
                                launchSingleTop = true
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    if (getPlatform().isGeneratePlayerShortcutSupported()) {
                        val context = getPlatform().getContext()
                        NavigationDrawerItem(
                            label = { Text("Add players to home screen") },
                            selected = false,
                            onClick = {
                                val characters = state.characters.mapNotNull { it.name }
                                if (characters.isNotEmpty()) {
                                    getPlatform().generatePlayerShortcut(context, characters)
                                } else {
                                    scope.launch {
                                        snackBarHostState.showSnackbar(
                                            message = "No characters to add",
                                            withDismissAction = true
                                        )
                                    }
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally).padding(all = 10.dp)) {
                        Column() {
                            Text("${state.actions.filterIsInstance<StartTurn>().size} turns played so far in current encounter")
                            Text("${state.characters.filter { !it.dead }.size} characters still alive")
                            Button(onClick = {
                                scope.launch {
                                    drawerState.close()
                                    val snackbarResult = snackBarHostState.showSnackbar(
                                        "Really delete current encounter?",
                                        actionLabel = "Yes",
                                        withDismissAction = true
                                    )
                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                        model.deleteNewerActions(0)
                                    }
                                }
                            }) {
                                Text("Start new encounter")
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(
                snackbarHost = {
                    SnackbarHost(hostState = snackBarHostState )

                    Napier.i("hello from Napier")

                    LaunchedEffect(Unit) {
                        ClientConsumer.clientStatus.collect {
                            when (it.status) {
                                is ClientStatusState.ConnectionError -> {
                                    Napier.i("want to show snackbar ")
                                    snackBarHostState.showSnackbar(
                                        "Client disconnected: " + it.status.errorMessage,
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Long
                                    )
                                }
                                else -> {}
                            }

                            Napier.i("collect client status: " + it + " ")
                        }
                    }
                },
                topBar = {
                    TopAppBar(
                        windowInsets = TopAppBarDefaults.windowInsets,
                        title = {
                            val currentScreen = Screens.valueOf(
                                backStackEntry?.destination?.route ?: Screens.MainScreen.name
                            )
                            Text(currentScreen.title, maxLines = 1,overflow = TextOverflow.Ellipsis)
                        },
                        actions = {
                            UpDownloadState()
                            ConnectionState()
                            val serverStatus by getPlatform().serverStatus
                            val clientStatus by ClientConsumer.clientStatus.collectAsState()
                            val context = getPlatform().getContext()
                            IconButton(enabled = serverStatus.isRunning && serverStatus.joinLinks.isNotEmpty() || !serverStatus.isRunning && clientStatus.status is ClientStatusState.Running, onClick = {
                                val links = if (serverStatus.isRunning && serverStatus.joinLinks.isNotEmpty()) {
                                    Pair(serverStatus.joinLinks[0], serverStatus.joinLinks)
                                } else {
                                    Pair(JoinLink(clientStatus.host), listOf())
                                }
                                getPlatform().shareLink(context, links.first, links.second)
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share join link")
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screens.MainScreen.name
                ) {
                    composable(route = Screens.MainScreen.name) {
                        MainScreen(innerPadding, state, model, viewStateVar, pagerState)
                    }

                    composable(route = Screens.ListActions.name) {
                        ListActions(innerPadding, state, model)
                    }

                    composable(route = Screens.ConnectionSettings.name) {
                        ConnectionSettings(innerPadding, model, globalCoroutineScope)
                    }

                    composable(route = Screens.Parties.name) {
                        Parties(innerPadding, model, partiesState, navigateToCharacters = {
                            navController.navigate(Screens.MainScreen.name) {
                                popUpTo(Screens.MainScreen.name)
                                launchSingleTop = true
                            }
                            viewState = viewState.copy(shownView = ShownView.CHARACTERS)
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun UpDownloadState() {
    val _sendUpdates by sendUpdates.collectAsState()
    val _receiveUpdates by receiveUpdates.collectAsState()
    val sendResource = if (_sendUpdates) {
        Res.drawable.baseline_file_upload_24
    } else {
        Res.drawable.baseline_file_upload_off_24
    }
    val receiveResource = if (_receiveUpdates) {
        Res.drawable.baseline_file_download_24
    } else {
        Res.drawable.baseline_file_download_off_24
    }

    IconToggleButton(
        checked = _sendUpdates,
        onCheckedChange = { checked ->
            sendUpdates.update { checked }
        },
        content = {
            val vector = vectorResource(sendResource)
            Icon(imageVector = vector, contentDescription = "Not Synced")

        }
    )

    IconToggleButton(
        checked = _receiveUpdates,
        onCheckedChange = { checked ->
            receiveUpdates.update { checked }
        },
        content = {
            val vector = vectorResource(receiveResource)
            Icon(imageVector = vector, contentDescription = "Not Synced")
        }
    )
}

@Composable
fun ConnectionState(modifier: Modifier = Modifier, transform: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
    val clientStatus by ClientConsumer.clientStatus.collectAsState()
    val serverStatus by getPlatform().serverStatus
    BadgedBox(modifier = modifier,badge = {
        if (serverStatus.isRunning) {
            Badge { Text(serverStatus.connections.toString()) }
        }
    }) {
        transform {
            if (clientStatus.status is ClientStatusState.ConnectionError) {
                val vector = vectorResource(Res.drawable.baseline_sync_problem_24)
                Icon(imageVector = vector, contentDescription = "Sync problem")
            } else if (clientStatus.status is ClientStatusState.Running || serverStatus.isRunning) {
                val vector = vectorResource(Res.drawable.baseline_sync_24)
                Icon(imageVector = vector, contentDescription = "Synced")
            } else {
                val vector = vectorResource(Res.drawable.baseline_sync_disabled_24)
                Icon(imageVector = vector, contentDescription = "Not Synced")
            }
        }
    }
}

@Composable
fun ConnectionSettings(innerPadding: PaddingValues, model: Model, coroutineScope: CoroutineScope) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.padding(innerPadding).verticalScroll(
        scrollState
    )) {
        val serverStatus by getPlatform().serverStatus
        val clientStatus by ClientConsumer.clientStatus.collectAsState()
        val context = getPlatform().getContext()
        ListItem(
            headlineContent = { Text("Function as Server") },
            trailingContent = {
                Switch(checked = serverStatus.isRunning, enabled = serverStatus.isSupported, onCheckedChange = {
                    getPlatform().toggleServer(model, context)
                })
            }
        )
        ListItem(
            headlineContent = { Text("Server status") },
            supportingContent = {
                Text(serverStatus.message + "\nConnections: ${serverStatus.connections}")
            }
        )
        serverStatus.joinLinks.forEach { joinLink ->
            ListItem(
                headlineContent = { Text("Join Link") },
                supportingContent = {
                    Text(joinLink.toUrl())
                },
                modifier = Modifier.clickable {
                    getPlatform().shareLink(context, joinLink, serverStatus.joinLinks)
                }
            )
        }
        ListItem(
            headlineContent = { Text("Function as Client") },
            trailingContent = {
                Switch(checked = clientStatus.status is ClientStatusState.Running, enabled = clientStatus.status !is ClientStatusState.Starting, onCheckedChange = {
                    ClientConsumer.toggleClient(model, coroutineScope)
                })
            }
        )
        ListItem(
            headlineContent = {
                if (clientStatus.status is ClientStatusState.Running || clientStatus.status is ClientStatusState.Starting) {
                    Text("Host: ${clientStatus.host}")
                } else {
                    TextField(
                        value = clientStatus.host,
                        label = { Text("Host") },
                        singleLine = true,
                        onValueChange = {
                            ClientConsumer.changeHost(it)
                        })
                }
            }
        )
        ListItem(
            headlineContent = { Text("Client status") },
            supportingContent = {
                Text(when (val status = clientStatus.status) {
                    is ClientStatusState.ConnectionError -> "Connection error: ${status.errorMessage}"
                    is ClientStatusState.Running -> "Running (${status.receivedSuccesfulFrames} updates received so far)"
                    is ClientStatusState.Starting -> "Starting"
                    is ClientStatusState.Stopped -> "Stopped"
                })
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Send updates") },
            trailingContent = {
                val x by sendUpdates.collectAsState()
                Switch(checked = x, onCheckedChange = {
                    sendUpdates.value = it
                })
            }
        )
        ListItem(
            headlineContent = { Text("Apply received updates") },
            trailingContent = {
                val x by receiveUpdates.collectAsState()
                Switch(checked = x, onCheckedChange = {
                    receiveUpdates.value = it
                })
            }
        )
    }
}

data class SimpleCharacter(val id: Int, val name: String, val playerCharacter: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val color = when (dismissState.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Color(0xFFFF1744)
        SwipeToDismissBoxValue.EndToStart -> Color(0xFF1DE9B6)
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "delete"
        )
        Spacer(modifier = Modifier)
        Icon(
            // make sure add baseline_archive_24 resource to drawable folder
            Icons.Default.Edit,
            contentDescription = "Archive"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Parties(innerPadding: PaddingValues, model: Model, partiesState: MutableState<Map<String, List<SimpleCharacter>>>, navigateToCharacters: () -> Unit) {
    var parties by partiesState
    var nextId by remember { mutableStateOf(0) }
    var currentEditParty by remember { mutableStateOf<Pair<String, List<SimpleCharacter>>?>(null) }
    var currentShowParty by remember { mutableStateOf<String?>(null) }

    if (currentEditParty == null && currentShowParty == null) {
        Column(modifier = Modifier.padding(innerPadding).fillMaxWidth().fillMaxHeight()) {
            ListItem(headlineContent = { Text("Manage characters") })
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items = parties.entries.toList(), key = { it.key }) { party ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            when (it) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    parties = parties - party.key
                                    true
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    currentEditParty = party.key to parties[party.key].orEmpty().map { it.copy(id = ++nextId) }
                                    parties = parties - party.key
                                    true
                                }
                                SwipeToDismissBoxValue.Settled -> false
                            }
                        }
                    )
                    SwipeToDismissBox(state = dismissState, backgroundContent = { DismissBackground(dismissState) } ) {
                        ListItem(
                            modifier = Modifier.clickable { currentShowParty = party.key },
                            headlineContent = { Text(party.key) },
                            supportingContent = {
                                Text(party.value.joinToString(", ") { it.name })
                            }
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                modifier = Modifier.padding(all = 20.dp).align(Alignment.End),
                onClick = { currentEditParty = "" to listOf(SimpleCharacter(++nextId, "", true)) }
            ) { Text("Add new party") }
        }
    } else if (currentShowParty != null) {
        Column(modifier = Modifier.padding(innerPadding)) {
            val characters = parties[currentShowParty].orEmpty()
            var unselected by remember { mutableStateOf(emptySet<String>()) }

            Column(modifier = Modifier.weight(1f)) {
                for (character in characters) {
                    key(character) {
                        ListItem(headlineContent = { Text(character.name) },
                            leadingContent = {
                                Checkbox(
                                    checked = character.name !in unselected,
                                    onCheckedChange = {
                                        if (!it) {
                                            unselected = unselected + character.name
                                        } else {
                                            unselected = unselected - character.name
                                        }
                                    })
                            })
                    }
                }
            }

            Column(modifier = Modifier.padding(all = 20.dp).align(Alignment.CenterHorizontally)) {
                ExtendedFloatingActionButton(onClick = {
                    val validCharacters = characters.filter { it.name !in unselected }
                    val str = validCharacters.ifEmpty { null }?.joinToString { it.name }
                    model.addCharacters(str)
                    navigateToCharacters()
                }) { Text("Add party to encounter") }
            }
        }
    } else if (currentEditParty != null) {
        Column(modifier = Modifier.padding(innerPadding)) {
            Column(modifier = Modifier.weight(1f)) {
                ListItem(headlineContent = {
                    TextField(
                        value = currentEditParty!!.first,
                        label = { Text("Name of party") },
                        onValueChange = {
                            currentEditParty = it to currentEditParty!!.second
                        },
                        singleLine = true
                    )
                })
                ListItem(headlineContent = { Text("Characters") })
                for (character in currentEditParty!!.second) {
                    key(character.id) {
                        ListItem(headlineContent = {
                            TextField(
                                value = character.name,
                                singleLine = true,
                                onValueChange = {
                                    Napier.i("Value Change to '$it' in $character")
                                    currentEditParty = currentEditParty!!.copy(
                                        second = currentEditParty!!.second.toMutableList().apply {
                                            val index = currentEditParty!!.second.indexOfFirst { it.id == character.id }
                                            if (it.isNotBlank() || it.isBlank() && index == currentEditParty!!.second.size - 1) {
                                                this[index] =
                                                    this[index].copy(name = it)
                                            } else if (it.isBlank()) {
                                                this.removeAt(index)
                                            }
                                            if (index == currentEditParty!!.second.size - 1 && it.isNotBlank()) {
                                                nextId += 1
                                                this.add(SimpleCharacter(id = nextId, name = "", playerCharacter = true))
                                            }
                                        }
                                    )
                                }
                            )
                        })
                    }
                }
            }
            ExtendedFloatingActionButton(modifier = Modifier.padding(all = 20.dp).align(Alignment.End), onClick = {
                if (currentEditParty!!.first != "" && currentEditParty!!.first !in parties.keys && currentEditParty!!.second.filter { it.name.isNotBlank() }.isNotEmpty())
                parties = parties + (currentEditParty!!.first to currentEditParty!!.second.filter { it.name.isNotBlank() }.toList())
                currentEditParty = null
            }) { Text("Add this party") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(innerPadding: PaddingValues, state: State, model: Model, viewStateVar: MutableState<ViewState>, pagerState: PagerState) {
    val actions: Actions = model
    var viewState by viewStateVar

    Column(Modifier.padding(innerPadding)) {
        PrimaryTabRow(
            selectedTabIndex = viewState.shownView.ordinal
        ) {
            Tab(selected = viewState.shownView == ShownView.CHARACTERS, onClick = {
                viewState = viewState.copy(shownView = ShownView.CHARACTERS)
            }, text = {
                Text("Characters")
            })
            Tab(selected = viewState.shownView == ShownView.TURNS, onClick = {
                viewState = viewState.copy(
                    shownView = ShownView.TURNS,
                    currentlyEditedCharacter = null
                )
            }, text = {
                Text("Turns")
            })
        }
        HorizontalPager(pagerState) { page ->
            val thisViewState = viewState.copy(shownView = ShownView.entries[page])
            Column {
                val listState = rememberLazyListState()
                InitOrder(
                    this,
                    state.characters,
                    state.currentlySelectedCharacter,
                    actions,
                    listState,
                    thisViewState
                ) {
                    viewState =
                        viewState.copy(currentlyEditedCharacter = if (viewState.currentlyEditedCharacter == it) null else it)
                }
                if (thisViewState.shownView == ShownView.TURNS) {
                    BottomAppBar(
                        windowInsets = BottomAppBarDefaults.windowInsets,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            OutlinedButton( onClick = {
                                model.delay()
                            }) {
                                Text("Delay turn")
                            }

                            Button(onClick = {
                                model.next()
                            }) {
                                Text("Start next turn")
                            }

                            OutlinedButton(onClick = {
                                state.currentlySelectedCharacter.let {
                                    if (it != null)
                                        model.finishTurn(it)
                                }
                            }) {
                                Text("Finish turn")
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
fun ListActions(innerPadding: PaddingValues, state: State, actions: Actions) {
    var showModalDialogOfIndex by remember { mutableStateOf<Int?>(null) }
    LazyColumn(contentPadding = innerPadding) {
        items(state.actions.withIndex().reversed()) { item ->
            Row(
                modifier =
                Modifier.clickable(onClick = {
                        showModalDialogOfIndex = item.index
                    })
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    text = descriptionOfAction(item.value)
                )
            }
        }
    }
    val modelDialogIndex = showModalDialogOfIndex
    if (modelDialogIndex != null) {
        BasicAlertDialog(
            onDismissRequest = { showModalDialogOfIndex = null },
            content = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column {
                        Text(
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                            textAlign = null,
                            text = "Delete actions menu",
                        )
                        HorizontalDivider()
                        Text(
                            text = descriptionOfAction(state.actions[modelDialogIndex]),
                            modifier = Modifier.padding(16.dp)
                        )

                        TextButton(
                            modifier = Modifier.align(Alignment.End),
                            onClick = { showModalDialogOfIndex = null },
                        ) {
                            Text("Dismiss")
                        }
                        TextButton(
                            modifier = Modifier.align(Alignment.End),
                            onClick = {
                                showModalDialogOfIndex = null
                                actions.deleteAction(modelDialogIndex)
                            },
                        ) {
                            Text("Undo action")
                        }
                        TextButton(
                            modifier = Modifier.align(Alignment.End),
                            onClick = {
                                showModalDialogOfIndex = null
                                actions.deleteNewerActions(modelDialogIndex)
                            },
                        ) {
                            Text("Undo action and all newer actions")
                        }

                    }
                }
            }
        )
    }
}

fun descriptionOfAction(action: ActionState): String {
    when (action) {
        is AddCharacter -> {
            return "Add character ${action.id}"
        }
        is ChangeName -> {
            return "Change name of character ${action.id} to ${action.name}"
        }
        is ChangeInitiative -> {
            return "Change initiative of character ${action.id} to ${action.initiative}"
        }
        is ChangePlayerCharacter -> {
            return "Change player character of character ${action.id} to ${action.playerCharacter}"
        }
        is DeleteCharacter -> {
            return "Delete character ${action.id}"
        }
        is StartTurn -> {
            return "Start turn of character ${action.id}"
        }
        is Delay -> {
            return "Delay turn of character ${action.id}"
        }
        is FinishTurn -> {
            return "Finish turn of character ${action.id}"
        }
        is Die -> {
            return "Character ${action.id} dies"
        }
    }
}
