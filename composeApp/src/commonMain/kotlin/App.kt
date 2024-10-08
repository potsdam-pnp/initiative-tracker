import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
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
import io.github.potsdam_pnp.initiative_tracker.State
import io.github.potsdam_pnp.initiative_tracker.TurnAction
import io.github.potsdam_pnp.initiative_tracker.crdt.ConflictState
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.baseline_sync_24
import kotlinproject.composeapp.generated.resources.baseline_sync_disabled_24
import kotlinproject.composeapp.generated.resources.baseline_sync_problem_24
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
fun ShowCharacter(uiCharacter: UiCharacter, isActive: Boolean, actions: Actions, viewState: ViewState, isGreyed: Boolean = false, toggleEditCharacter: (String) -> Unit) {
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
            if (viewState.shownView == ShownView.TURNS && !uiCharacter.dead) {
                Text("${uiCharacter.turn + 1}", )
            }
        }
        if (!uiCharacter.dead) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                if (viewState.currentlyEditedCharacter != uiCharacter.key) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(uiCharacter.name ?: "")
                        ShowPlayerVsNonPlayerCharacter(viewState, uiCharacter, actions)
                        if (uiCharacter.isDelayed && viewState.shownView == ShownView.TURNS) {
                            Text(
                                "Delayed",
                                Modifier.padding(horizontal = 10.dp),
                                fontStyle = FontStyle.Italic
                            )
                            OutlinedButton(onClick = { actions.startTurn(uiCharacter.key) }) {
                                Text("Take")
                            }
                        }
                    }
                } else {
                    TextField(
                        modifier = if (uiCharacter.name == null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                        singleLine = true,
                        value = uiCharacter.name ?: "",
                        onValueChange = { actions.editCharacter(uiCharacter.key, it) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                toggleEditCharacter(uiCharacter.key)
                            },
                            onNext = {
                                focusManager.moveFocus(FocusDirection.Next)
                            }
                        ),
                        label = { Text("Name") })
                    if (uiCharacter.name == null || uiCharacter.initiative == null) {
                        DisposableEffect(Unit) {
                            focusRequester.requestFocus()
                            onDispose { }
                        }
                    }
                }
            }
            val toggleEditIcon = if (viewState.currentlyEditedCharacter != uiCharacter.key) {
                Icons.Default.Edit
            } else {
                Icons.Default.Check
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (viewState.currentlyEditedCharacter != uiCharacter.key) {
                        Text(
                            modifier = Modifier.padding(horizontal = 5.dp),
                            text = uiCharacter.initiative?.toString() ?: ""
                        )
                    } else {
                        TextField(
                            modifier = Modifier.width(70.dp)
                                .padding(horizontal = 5.dp)
                                .then(
                                    if (uiCharacter.initiative == null && uiCharacter.name != null) Modifier.focusRequester(
                                        focusRequester
                                    ) else Modifier
                                ),
                            singleLine = true,
                            value = uiCharacter.initiative?.toString() ?: "",
                            onValueChange = { actions.editInitiative(uiCharacter.key, it) },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Decimal
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    toggleEditCharacter(uiCharacter.key)
                                },
                                onPrevious = {
                                    focusManager.moveFocus(FocusDirection.Previous)
                                }
                            ),
                            label = { Text("In") })
                    }
                    if (viewState.shownView == ShownView.CHARACTERS) {
                        IconButton(onClick = { toggleEditCharacter(uiCharacter.key) }) {
                            AnimatedContent(targetState = toggleEditIcon) {
                                Icon(it, contentDescription = "Toggle Edit")
                            }
                        }
                        IconButton(onClick = { actions.deleteCharacter(uiCharacter.key) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete"
                            )
                        }
                    } else {
                        if (uiCharacter.playerCharacter == true) {
                            IconButton(enabled = !isGreyed, onClick = { actions.die(uiCharacter.key) }) {
                                Icon(
                                    imageVector = deathIcon,
                                    tint = Color.Black,
                                    contentDescription = "Get dying condition"
                                )
                            }
                        } else {
                            IconButton(enabled = !isGreyed, onClick = { actions.deleteCharacter(uiCharacter.key) }) {
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
fun ShowPlayerVsNonPlayerCharacter(viewState: ViewState, uiCharacter: UiCharacter, actions: Actions) {
    val isPlayerCharacter = uiCharacter.playerCharacter == true
    if (viewState.shownView == ShownView.CHARACTERS) {
        Button(modifier = Modifier.padding(start = 10.dp), onClick = { actions.togglePlayerCharacter(uiCharacter.key, !isPlayerCharacter) }) {
            Text(text = if (isPlayerCharacter) "PC" else "NPC")
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListCharacters(
    columnScope: ColumnScope,
    uiCharacters: List<UiCharacter>,
    actions: Actions,
    listState: LazyListState,
    currentlyEditedCharacter: String?,
    toggleEditCharacter: (String) -> Unit) {
    LazyColumn(state = listState, modifier = with(columnScope) { Modifier.fillMaxWidth().weight(1f) }) {
        items(uiCharacters, key = { it.key }) { character ->
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

@Composable
fun ListConflictTurns(columnScope: ColumnScope, hasConflict: Boolean, showActionList: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = with(columnScope) { Modifier.fillMaxWidth().weight(1f) }) {
        if (hasConflict) {
            ExtendedFloatingActionButton(
                modifier = Modifier.align(Alignment.BottomEnd).padding(all = 20.dp),
                onClick = {
                    showActionList()
                }
            ) {
                Text("Resolve conflicts")
            }
        }
        Box(
            modifier = if (hasConflict) Modifier.background(Color.Transparent)
                .blur(4.dp) else Modifier
        ) {
            content()
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListTurns(uiCharacters: List<UiCharacter>, active: String?, actions: Actions) {
    SubcomposeLayout(modifier = Modifier.clipToBounds()) { constraints ->
        if (uiCharacters.isEmpty()) {
            return@SubcomposeLayout layout(0, 0) {}
        }

        var currentAddTurn = -1
        var currentIndex = uiCharacters.size - 1
        var currentHeight = 0

        var oneRoundHeight = 0

        val placeables = mutableListOf<Pair<Int, Placeable>>()

        while (currentHeight < constraints.maxHeight) {
            val currentCharacter = uiCharacters[currentIndex]
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
            if (currentIndex == uiCharacters.size) {
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
fun InitOrder(columnScope: ColumnScope, uiCharacters: List<UiCharacter>, active: String?, actions: Actions, listState: LazyListState, viewState: ViewState, hasConflict: Boolean, showActionList: () -> Unit, toggleEditCharacter: (String) -> Unit) {
    if (viewState.shownView == ShownView.CHARACTERS) {
        ListCharacters(columnScope, uiCharacters, actions, listState, viewState.currentlyEditedCharacter, toggleEditCharacter)
    } else {
        ListConflictTurns(columnScope, hasConflict, showActionList) {
            ListTurns(uiCharacters, active, actions)
        }
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
    val model = viewModel { Model(Repository(State()), data) }
    LaunchedEffect(Unit) {
        val predefinedServerHost = data?.split("&")?.firstOrNull { it.startsWith("server=") }?.let {
            it.substring(7)
        }
        if (predefinedServerHost != null) {
            ClientConsumer.changeHost(predefinedServerHost)
            ClientConsumer.start(model, globalCoroutineScope)
        }
    }
    val uiState by model.state.collectAsState(UiState(turnConflicts = false))

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
                                val characters = uiState.characters.mapNotNull { it.name }
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
                            Text("${uiState.actions.filterIsInstance<TurnAction.StartTurn>().size} turns played so far in current encounter")
                            Text("${uiState.characters.filter { !it.dead }.size} characters still alive")
                            Button(onClick = {
                                scope.launch {
                                    drawerState.close()
                                    val snackbarResult = snackBarHostState.showSnackbar(
                                        "Really delete current encounter?",
                                        actionLabel = "Yes",
                                        withDismissAction = true
                                    )
                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                        model.restartEncounter()
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
                            ConnectionState()
                            val serverStatus = getPlatform().serverStatus()
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
                        MainScreen(innerPadding, uiState, model, viewStateVar, pagerState) {
                            navController.navigate(Screens.ListActions.name) {
                                popUpTo(Screens.MainScreen.name)
                                launchSingleTop = true
                            }
                        }
                    }

                    composable(route = Screens.ListActions.name) {
                        ListActions(innerPadding, uiState, model)
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
fun ConnectionState(modifier: Modifier = Modifier, transform: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
    val clientStatus by ClientConsumer.clientStatus.collectAsState()
    val serverStatus = getPlatform().serverStatus()
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
fun ServerConnectionSettings() {
    val serverStatus = getPlatform().serverStatus()

    ListItem(headlineContent = { Text(serverStatus.message )})
    for (connectedClient in serverStatus.discoveredClients) {
        val connectionState = @Composable {
            if (connectedClient.isClientConnected || connectedClient.isServerConnected) {
                Icon(
                    imageVector = vectorResource(Res.drawable.baseline_sync_24),
                    contentDescription = "Synced"
                )
            } else {
                Icon(
                    imageVector = vectorResource(Res.drawable.baseline_sync_problem_24),
                    contentDescription = "Not synced"
                )
            }
        }

        val additional =
            when {
                connectedClient.isServerConnected && connectedClient.isClientConnected ->
                    listOf("as server and client")

                connectedClient.isClientConnected ->
                    listOf("as server")

                connectedClient.isServerConnected ->
                    listOf("as client")

                else ->
                    emptyList()
            } + connectedClient.errorMsg.orEmpty()

        key(connectedClient.name) {
            ListItem(
                headlineContent = { Text(connectedClient.name) },
                trailingContent = { connectionState() },
                supportingContent = {
                    val text = (connectedClient.hosts.orEmpty() + additional).joinToString("\n")
                    if (text != "") {
                        Text(text = text)
                    }
                },
            )
        }
    }
    HorizontalDivider()
}

@Composable
fun ConnectionSettings(innerPadding: PaddingValues, model: Model, coroutineScope: CoroutineScope) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.padding(innerPadding).verticalScroll(
        scrollState
    )) {
        val serverStatus = getPlatform().serverStatus()
        val clientStatus by ClientConsumer.clientStatus.collectAsState()
        val context = getPlatform().getContext()
        ServerConnectionSettings()
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Connect manually") },
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
fun MainScreen(innerPadding: PaddingValues, uiState: UiState, model: Model, viewStateVar: MutableState<ViewState>, pagerState: PagerState, showActionList: () -> Unit) {
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
                    uiState.characters,
                    uiState.currentlySelectedCharacter,
                    actions,
                    listState,
                    thisViewState,
                    uiState.turnConflicts,
                    showActionList,
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
                            OutlinedButton( enabled = !uiState.turnConflicts, onClick = {
                                model.delay()
                            }) {
                                Text("Delay turn")
                            }

                            Button(enabled = !uiState.turnConflicts, onClick = {
                                model.next()
                            }) {
                                Text("Start next turn")
                            }

                            OutlinedButton(enabled = !uiState.turnConflicts, onClick = {
                                uiState.currentlySelectedCharacter.let {
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
fun ListActions(innerPadding: PaddingValues, uiState: UiState, actions: Actions) {
    var showModalDialogOfDot by remember { mutableStateOf<Dot?>(null) }
    LazyColumn(contentPadding = innerPadding) {
        items(uiState.actions.reversed(), key = { it.first.clientIdentifier.name + "-" + it.first.position }) { item ->
            Row(
                modifier =
                Modifier.clickable(onClick = {
                        showModalDialogOfDot = item.first
                    })
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    text = descriptionOfAction(item)
                )
            }
        }
    }
    val modelDialogVersion = showModalDialogOfDot
    if (modelDialogVersion != null) {
        BasicAlertDialog(
            onDismissRequest = { showModalDialogOfDot = null },
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
                            text = descriptionOfAction(uiState.actions.find { it.first == modelDialogVersion }!!),
                            modifier = Modifier.padding(16.dp)
                        )

                        TextButton(
                            modifier = Modifier.align(Alignment.End),
                            onClick = { showModalDialogOfDot = null },
                        ) {
                            Text("Dismiss")
                        }
                        TextButton(
                            modifier = Modifier.align(Alignment.End),
                            onClick = {
                                showModalDialogOfDot = null
                                actions.pickAction(modelDialogVersion)
                            },
                        ) {
                            Text("Pick as most recent action")
                        }
                    }
                }
            }
        )
    }
}

fun descriptionOfAction(action: Triple<Dot, ConflictState, TurnAction>): String {
    val conflictStateString =
        when (val af = action.second) {
            ConflictState.InAllTimelines -> ""
            is ConflictState.InTimelines -> "in timelines ${af.timeline.joinToString { it.toString()}}: "
        }

    val result = when (val a = action.third) {
        is TurnAction.StartTurn -> {
            "Start turn of character ${a.characterId}"
        }
        is TurnAction.Delay -> {
            "Delay turn of character ${a.characterId}"
        }
        is TurnAction.FinishTurn -> {
            "Finish turn of character ${a.characterId}"
        }
        is TurnAction.Die -> {
            "Character ${a.characterId} dies"
        }
        is TurnAction.ResolveConflicts -> {
            "Undo some actions"
        }
    }

    return conflictStateString + result
}
