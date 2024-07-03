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
import androidx.compose.material.IconToggleButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.compose_multiplatform
import kotlin.math.roundToInt


sealed class Character {
    abstract val key: String

    data class Finished(override val key: String, val name: String, val initiative: Int, val playerCharacter: Boolean): Character()
    data class Edit(override val key: String, val name: String?, val initiative: Int?, val playerCharacter: Boolean, val focusRequester: FocusRequester = FocusRequester()): Character()
}

interface Actions {
    fun deleteCharacter(characterKey: String)
    fun editCharacter(characterKey: String, name: String?)
    fun editInitiative(characterKey: String, initiative: String)
    fun toggleEditCharacter(characterKey: String)
    fun moveCharacterUp(characterKey: String)
    fun moveCharacterDown(characterKey: String)
}

@Composable
fun ShowCharacter(character: Character, isActive: Boolean, actions: Actions, editMode: Boolean) {
    var modifier: Modifier = Modifier.fillMaxWidth();
    if (isActive) {
        modifier = modifier.then(Modifier.background(color = Color.Yellow))
    }
    modifier = modifier.then(Modifier.padding(vertical = 10.dp, horizontal = 20.dp).heightIn(min = 60.dp))

    val focusManager = LocalFocusManager.current

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(30.dp)) {
            if (editMode) {
                IconButton(modifier = Modifier.padding(0.dp).size(24.dp), onClick = {
                    actions.moveCharacterUp(character.key)
                }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(modifier = Modifier.padding(0.dp).size(24.dp), onClick = {
                    actions.moveCharacterDown(character.key)

                }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
            }
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            when (character) {
                is Character.Finished -> Text(character.name)
                is Character.Edit -> {
                    TextField(
                        modifier = Modifier.focusRequester(character.focusRequester),
                        singleLine = true,
                        value = character.name ?: "",
                        onValueChange = { actions.editCharacter(character.key, it) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                actions.toggleEditCharacter(character.key)
                            },
                            onNext = {
                                focusManager.moveFocus(FocusDirection.Next)
                            }
                        ),
                        label = { Text("Name") })
                    DisposableEffect(Unit) {
                        character.focusRequester.requestFocus()
                        onDispose { } // Optional cleanup if needed
                    }
                }
            }
        }
        val toggleEditIcon = when (character) {
            is Character.Finished -> Icons.Default.Edit
            is Character.Edit -> Icons.Default.Check
        }
        Column() {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (character) {
                    is Character.Finished ->
                        Text(modifier = Modifier.padding(horizontal = 5.dp), text = character.initiative.toString())
                    is Character.Edit ->
                    TextField(
                        modifier = Modifier.width(70.dp).padding(horizontal = 5.dp),
                        singleLine = true,
                        value = character.initiative?.toString() ?: "",
                        onValueChange = { actions.editInitiative(character.key, it) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Decimal),
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
                if (editMode) {
                    IconButton(onClick = { actions.toggleEditCharacter(character.key) }) {
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
                }
            }
        }
    }
}



@Composable
@OptIn(ExperimentalFoundationApi::class)
fun InitOrder(innerPadding: PaddingValues, characters: List<Character>, active: String, actions: Actions, listState: LazyListState, editMode: Boolean) {
    LazyColumn(contentPadding = innerPadding, state = listState) {
        items(
            characters,
            key = { it.key }
        ) { character ->
            @Suppress("EXPERIMENTAL_FOUNDATION_API_USAGE")
            Box(modifier = Modifier.animateItemPlacement()) {
                ShowCharacter(character, isActive = character.key == active, actions, editMode)
            }
        }
    }
}


var lastKey = 0;

fun nextKey(): String {
    lastKey += 1;
    return lastKey.toString();
}


@Composable
@Preview
fun App() {
    MaterialTheme {
        var characterList by remember { mutableStateOf(listOf<Character>()) }
        var currentlySelectedCharacter by remember { mutableStateOf("") };
        val listState = rememberLazyListState()
        var editCharacter by remember { mutableStateOf("") }
        var editMode by remember { mutableStateOf(false) }

        val actions = object : Actions {
            override fun deleteCharacter(characterKey: String) {
                characterList = characterList.filter { it.key != characterKey }
            }

            override fun editCharacter(characterKey: String, name: String?) {
                characterList = characterList.map {
                    if (it.key == characterKey && it is Character.Edit) {
                        it.copy(name = name)
                    } else {
                        it
                    }
                }
            }

            override fun editInitiative(characterKey: String, initiative: String) {
                if (initiative.toIntOrNull() == null && initiative != "") return
                characterList = characterList.map {
                    if (it.key == characterKey && it is Character.Edit) {
                        it.copy(initiative = initiative.toIntOrNull())
                    } else {
                        it
                    }
                }
            }

            override fun toggleEditCharacter(characterKey: String) {
                characterList = characterList.map {
                    if (it.key == characterKey) {
                        when (it) {
                            is Character.Edit -> Character.Finished(it.key, it.name ?: "", it.initiative ?: 0, it.playerCharacter)
                            is Character.Finished -> Character.Edit(it.key, it.name, it.initiative, it.playerCharacter)
                        }
                    } else {
                        it
                    }
                }
            }

            override fun moveCharacterUp(characterKey: String) {
                characterList = characterList.toMutableList().also {
                    val currentIndex = it.indexOfFirst { it.key == characterKey }
                    if (currentIndex >= 1) {
                        val currentCharacter = it[currentIndex]
                        val previousCharacter = it[currentIndex - 1]
                        it[currentIndex] = previousCharacter
                        it[currentIndex - 1] = currentCharacter
                    } else if (currentIndex == 0) {
                        val currentCharacter = it[currentIndex]
                        it.removeAt(0)
                        it.add(currentCharacter)
                    }
                }
            }

            override fun moveCharacterDown(characterKey: String) {
                characterList = characterList.toMutableList().also {
                    val currentIndex = it.indexOfFirst { it.key == characterKey }
                    if (currentIndex < it.size - 1 && currentIndex >= 0) {
                        val currentCharacter = it[currentIndex]
                        val nextCharacter = it[currentIndex + 1]
                        it[currentIndex] = nextCharacter
                        it[currentIndex + 1] = currentCharacter
                    } else if (currentIndex == it.size - 1) {
                        val currentCharacter = it[currentIndex]
                        it.removeAt(currentIndex)
                        it.add(0, currentCharacter)
                    }
                }
            }
        }

        //LaunchedEffect(key1 = currentlySelectedCharacter) {
        //    val offset = characterList.indexOfFirst { it.key == currentlySelectedCharacter }
        //    if (offset >= 0) {
        //        listState.animateScrollToItem(offset)
        //    }
        //}

        //LaunchedEffect(key1 = editCharacter) {
        //    val offset = characterList.indexOfFirst { it.key == editCharacter }
        //    if (offset >= 0) {
        //        listState.animateScrollToItem(offset)
        //    }
        // }

        Scaffold(
            topBar = {
                TopAppBar(
                    windowInsets = AppBarDefaults.topAppBarWindowInsets,
                    title = { Text("Initiative Tracker") },
                    actions = {
                        IconToggleButton(checked = editMode, enabled = characterList.all { it is Character.Finished }, onCheckedChange = {
                            editMode = it
                        }) {
                            if (editMode) {
                                Icon(Icons.Default.Done, contentDescription = "Done")
                            } else {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                BottomAppBar(
                    windowInsets = AppBarDefaults.bottomAppBarWindowInsets,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        if (editMode) {
                            Button(onClick = {
                                characterList = characterList.sortedBy { c ->
                                    when (c) {
                                        is Character.Finished -> -c.initiative
                                        is Character.Edit -> 1
                                    }
                                }
                            }, enabled = characterList.isNotEmpty() && characterList.all { it is Character.Finished }) {
                                Text("Sort")
                            }
                            IconButton(onClick = {
                                val key = nextKey()
                                val next = Character.Edit(key, null, null, false, FocusRequester());
                                characterList = characterList + next
                                editCharacter = key
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                            }
                        } else {

                            Button(onClick = {
                                val currentIndex =
                                    characterList.indexOfFirst { it.key == currentlySelectedCharacter }
                                if (currentIndex >= 0) {
                                    var nextIndex = currentIndex + 1

                                    if (nextIndex >= characterList.size) {
                                        nextIndex = 0
                                    }
                                    currentlySelectedCharacter = characterList[nextIndex].key

                                    characterList = characterList.toMutableList().also {
                                        val currentCharacter = it[currentIndex]
                                        val nextCharacter = it[nextIndex]
                                        it[currentIndex] = nextCharacter
                                        it[nextIndex] = currentCharacter

                                        if (nextIndex == 0) {
                                            // It's better to keep the character at the top instead of moving it to the end
                                            it.removeAt(currentIndex)
                                            it.add(0, nextCharacter)
                                        }
                                    }
                                }
                            }) {
                                Text("Delay")
                            }

                            Button(onClick = {
                                if (characterList.isNotEmpty()) {
                                    val currentIndex =
                                        characterList.indexOfFirst { it.key == currentlySelectedCharacter }
                                    var nextIndex = currentIndex + 1
                                    if (nextIndex >= characterList.size) {
                                        nextIndex = 0;
                                    }
                                    currentlySelectedCharacter = characterList[nextIndex].key
                                }
                            }) {
                                Text("Next")
                            }
                        }
                    }
                }
            },
            //floatingActionButton = {
            //    FloatingActionButton(onClick = {
            //        if (characterList.isNotEmpty()) {
            //            val currentIndex =
            //                characterList.indexOfFirst { it.key == currentlySelectedCharacter }
            //            var nextIndex = currentIndex + 1
            //            if (nextIndex >= characterList.size) {
            //                nextIndex = 0;
            //            }
            //            currentlySelectedCharacter = characterList[nextIndex].key
            //        }
            //    }) {
            //        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
            //    }
            //}

        ) { innerPadding ->
                InitOrder(innerPadding, characterList, currentlySelectedCharacter, actions, listState, editMode)

        }
    }
}