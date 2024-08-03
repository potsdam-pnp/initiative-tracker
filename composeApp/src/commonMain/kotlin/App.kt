import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview


enum class ShownView {
    CHARACTERS,
    TURNS
}

data class ViewState(
    val shownView: ShownView,
    val currentlyEditedCharacter: String?
)


@Composable
fun ShowCharacter(character: Character, isActive: Boolean, actions: Actions, viewState: ViewState, toggleEditCharacter: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }

    var modifier: Modifier = Modifier.fillMaxWidth()
    if (isActive && viewState.shownView == ShownView.TURNS) {
        modifier = modifier.then(Modifier.background(color = Color.Yellow))
    }
    modifier =
        modifier.then(Modifier.padding(vertical = 10.dp, horizontal = 20.dp).heightIn(min = 60.dp))

    val focusManager = LocalFocusManager.current

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(30.dp)) {
            //if (editMode) {
            //    IconButton(modifier = Modifier.padding(0.dp).size(24.dp), onClick = {
            //        actions.moveCharacterUp(character.key)
            //    }) {
            //        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
            //}
            //    IconButton(modifier = Modifier.padding(0.dp).size(24.dp), onClick = {
            //        actions.moveCharacterDown(character.key)

            //    }) {
            //        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
            //    }
            //}
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
                            IconButton(onClick = { actions.die(character.key) }) {
                                Icon(
                                    imageVector = deathIcon,
                                    tint = Color.Black,
                                    contentDescription = "Get dying condition"
                                )
                            }
                        } else {
                            IconButton(onClick = { actions.deleteCharacter(character.key) }) {
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


@Composable
@OptIn(ExperimentalFoundationApi::class)
fun InitOrder(columnScope: ColumnScope, characters: List<Character>, active: String?, actions: Actions, listState: LazyListState, viewState: ViewState, toggleEditCharacter: (String) -> Unit) {
    val listItems = characters.let {
        if (viewState.shownView == ShownView.CHARACTERS) {
            it + null
        } else {
            val first = it.firstOrNull()
            if (first != null) {
                it + first.copy(turn = first.turn + 1, dead = true)
            } else {
                it
            }
        }
    }
    LazyColumn(state = listState, modifier = with(columnScope) { Modifier.fillMaxWidth().weight(1f) }) {
        items(
            listItems,
            key = { (it?.key ?: "") + "-" + (it?.turn ?: "") }
        ) { character ->
            if (character != null) {
                @Suppress("EXPERIMENTAL_FOUNDATION_API_USAGE")
                Box(modifier = Modifier.animateItemPlacement()) {
                    ShowCharacter(character, isActive = character.key == active && !character.dead, actions, viewState, toggleEditCharacter)
                }
            } else {
                if (viewState.shownView == ShownView.CHARACTERS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = { actions.addCharacter() }) {
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

@Composable
fun SettingsMenu(characterList: List<Character>) {
    var showMenu by remember { mutableStateOf(false) }
    IconButton(onClick = { showMenu = !showMenu }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More")
    }
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        val characters = characterList.mapNotNull { it.name }
        getPlatform().DropdownMenuItemPlayerShortcut(enabled = characters.isNotEmpty()) {
            characters
        }
    }
}

enum class Screens(val title: String) {
    MainScreen("Initiative Tracker"),
    ListActions("List Actions"),
    ConnectionSettings("Connection Settings")
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@Preview
fun App(data: String? = null) {
    val globalCoroutineScope = rememberCoroutineScope()
    val model = viewModel { Model(data) }
    val state by model.state.collectAsState(State())

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

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text("Initiative Tracker", Modifier.padding(16.dp))
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text("Connection Settings") },
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
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        windowInsets = TopAppBarDefaults.windowInsets,
                        title = {
                            val currentScreen = Screens.valueOf(
                                backStackEntry?.destination?.route ?: Screens.MainScreen.name
                            )
                            Text(currentScreen.title)
                        },
                        actions = {
                            SettingsMenu(state.characters)
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
                }
            }
        }
    }
}

@Composable
fun ConnectionSettings(innerPadding: PaddingValues, model: Model, coroutineScope: CoroutineScope) {
    Column(modifier = Modifier.padding(innerPadding)) {
        val serverStatus by getPlatform().serverStatus.collectAsState()
        val clientStatus by ClientConsumer.clientStatus.collectAsState()
        ListItem(
            headlineContent = { Text("Function as Server") },
            trailingContent = {
                Switch(checked = serverStatus.isRunning, enabled = serverStatus.isSupported, onCheckedChange = {
                    getPlatform().toggleServer(model)
                })
            }
        )
        ListItem(
            headlineContent = { Text("Server status") },
            supportingContent = {
                Text(serverStatus.message)
            }
        )
        ListItem(
            headlineContent = { Text("Function as Client") },
            trailingContent = {
                Switch(checked = clientStatus.isRunning, onCheckedChange = {
                    ClientConsumer.toggleClient(model, coroutineScope)
                })
            }
        )
        ListItem(
            headlineContent = {
                if (clientStatus.isRunning) {
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
                Text(clientStatus.message)
            }
        )
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
