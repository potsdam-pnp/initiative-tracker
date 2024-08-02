import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.DraggableAnchorsConfig
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.BottomAppBar
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.BottomNavigation
import androidx.compose.material.DropdownMenu
import androidx.compose.material.IconToggleButton
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TextField
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel

import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.compose_multiplatform
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
fun ShowCharacter(character: Character, isActive: Boolean, actions: Actions, viewState: ViewState, toggleEditCharacter: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }

    var modifier: Modifier = Modifier.fillMaxWidth();
    if (isActive) {
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
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            if (viewState.currentlyEditedCharacter != character.key) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(character.name ?: "")
                    ShowPlayerVsNonPlayerCharacter(viewState, character, actions)
                    if (character.isDelayed) {
                        Text("Delayed", Modifier.padding(horizontal = 10.dp), fontStyle = FontStyle.Italic)
                        Button(onClick={ actions.startTurn(character.key) }) {
                            Text("Take")
                        }
                    }
                }
            } else {
                TextField(
                    modifier = if (character.name == null) { Modifier.focusRequester(focusRequester) } else { Modifier },
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
        Column() {
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
                        .then(if (character.initiative == null && character.name != null) Modifier.focusRequester(focusRequester) else Modifier),
                    singleLine = true,
                    value = character.initiative?.toString() ?: "",
                    onValueChange = { actions.editInitiative(character.key, it) },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Decimal
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            actions.toggleEditCharacter(character.key)
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


@Composable
fun ShowPlayerVsNonPlayerCharacter(viewState: ViewState, character: Character, actions: Actions) {
    val isPlayerCharacter = character.playerCharacter == true
    if (viewState.shownView == ShownView.CHARACTERS) {
        Button(modifier = Modifier.padding(start = 10.dp), onClick = { actions.togglePlayerCharacter(character.key) }) {
            Text(text = if (isPlayerCharacter) "PC" else "NPC")
        }
    }
}


@Composable
@OptIn(ExperimentalFoundationApi::class)
fun InitOrder(innerPadding: PaddingValues, characters: List<Character>, active: String?, actions: Actions, listState: LazyListState, viewState: ViewState, toggleEditCharacter: (String) -> Unit) {
    LazyColumn(contentPadding = innerPadding, state = listState) {
        items(
            characters + null,
            key = { it?.key ?: "" }
        ) { character ->
            if (character != null) {
                @Suppress("EXPERIMENTAL_FOUNDATION_API_USAGE")
                Box(modifier = Modifier.animateItemPlacement()) {
                    ShowCharacter(character, isActive = character.key == active, actions, viewState, toggleEditCharacter)
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
        getPlatform().DropdownMenuItemPlayerShortcut(enabled = characterList.isNotEmpty()) {
            characterList.mapNotNull {
               it.name
            }
        }
    }
}

@Composable
@Preview
fun App(data: String? = null) {
    val model = viewModel { Model(data) }
    val state by model.state.collectAsState(State())
    val actions: Actions = model

    var viewState by remember { mutableStateOf(ViewState(ShownView.CHARACTERS, null)) }

    MaterialTheme {
        val listState = rememberLazyListState()

        Scaffold(
            topBar = {
                TopAppBar(
                    windowInsets = AppBarDefaults.topAppBarWindowInsets,
                    title = { Text("Initiative Tracker") },
                    actions = {
                        SettingsMenu(state.characters)
                    }
                )
            },
            bottomBar = {
                if (viewState.shownView == ShownView.TURNS) {
                    BottomAppBar(
                        windowInsets = AppBarDefaults.bottomAppBarWindowInsets,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Button(onClick = {
                                model.delay()
                            }) {
                                Text("Delay")
                            }

                            Button(onClick = {
                                model.next()
                            }) {
                                Text("Next")
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Column() {
                TabRow(
                    selectedTabIndex = viewState.shownView.ordinal
                ) {
                    Tab(selected = viewState.shownView == ShownView.CHARACTERS, onClick = {
                        viewState = viewState.copy(shownView = ShownView.CHARACTERS)
                    }) {
                        Text("Characters")
                    }
                    Tab(selected = viewState.shownView == ShownView.TURNS, onClick = {
                        viewState = viewState.copy(shownView = ShownView.TURNS, currentlyEditedCharacter = null)
                    }) {
                        Text("Turns")
                    }
                }
                InitOrder(
                    innerPadding,
                    state.characters,
                    state.currentlySelectedCharacter,
                    actions,
                    listState,
                    viewState
                ) {
                    viewState =
                        viewState.copy(currentlyEditedCharacter = if (viewState.currentlyEditedCharacter == it) null else it)
                }
            }
        }
    }
}