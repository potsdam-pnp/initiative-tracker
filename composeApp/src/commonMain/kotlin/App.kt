import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import initiative_tracker.composeapp.generated.resources.*
import io.github.aakira.napier.Napier
import io.github.potsdam_pnp.initiative_tracker.CharacterId
import io.github.potsdam_pnp.initiative_tracker.State
import io.github.potsdam_pnp.initiative_tracker.TurnAction
import io.github.potsdam_pnp.initiative_tracker.crdt.ConflictState
import io.github.potsdam_pnp.initiative_tracker.crdt.Dot
import io.github.potsdam_pnp.initiative_tracker.crdt.Repository
import io.github.potsdam_pnp.initiative_tracker.crdt.VectorClock
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class ShownView {
  CHARACTERS,
  TURNS,
}

@Composable
fun ShowCharacter(
  uiCharacter: UiCharacter,
  currentlyEditedCharacter: CurrentlyEditedCharacter?,
  isActive: Boolean,
  actions: Actions,
  shownView: ShownView,
  isGreyed: Boolean = false,
) {
  val focusRequester = remember { FocusRequester() }

  var modifier: Modifier = Modifier.fillMaxWidth()
  if (shownView == ShownView.TURNS) {
    val isActiveAlpha by animateFloatAsState(if (isActive) 1f else 0f)
    modifier = modifier.then(Modifier.background(color = Color.Yellow.copy(alpha = isActiveAlpha)))
  }
  modifier =
    modifier.then(
      Modifier.padding(vertical = 10.dp, horizontal = 20.dp)
        .heightIn(min = 60.dp)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    )

  val focusManager = LocalFocusManager.current

  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.width(30.dp)) {
      if (shownView == ShownView.TURNS && !uiCharacter.dead) {
        Text("${uiCharacter.turn + 1}")
      }
    }
    if (!uiCharacter.dead) {
      Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
        if (currentlyEditedCharacter?.key != uiCharacter.key) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(uiCharacter.name?.asString() ?: "")
            ShowPlayerVsNonPlayerCharacter(shownView, uiCharacter, actions)
            if (uiCharacter.isDelayed && shownView == ShownView.TURNS) {
              Text(
                stringResource(Res.string.delayed),
                Modifier.padding(horizontal = 10.dp),
                fontStyle = FontStyle.Italic,
              )
              OutlinedButton(onClick = { actions.startTurn(uiCharacter.key) }) {
                Text(stringResource(Res.string.take_delayed))
              }
            }
          }
        } else {
          val persistedName = uiCharacter.name?.asString() ?: ""
          val value =
            currentlyEditedCharacter.positions
              .map { uiCharacter.name?.indexPosition(it) ?: 0 }
              .asTextFieldValue(persistedName)
          Napier.i("generated value: $value")

          val onValueChange = { newValue: TextFieldValue ->
            Napier.i("change value to $newValue (current: $value)")
            actions.updateName(uiCharacter.key, uiCharacter.name, newValue)
          }

          TextField(
            modifier =
              if (uiCharacter.name == null) {
                Modifier.focusRequester(focusRequester)
              } else {
                Modifier
              },
            singleLine = true,
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions =
              KeyboardActions(
                onDone = { actions.toggleEditCharacter(uiCharacter.key) },
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
              ),
            label = { Text(stringResource(Res.string.name)) },
          )
          if (uiCharacter.name == null || uiCharacter.initiative == null) {
            DisposableEffect(Unit) {
              focusRequester.requestFocus()
              onDispose {}
            }
          }
        }
      }
      val toggleEditIcon =
        if (currentlyEditedCharacter?.key != uiCharacter.key) {
          Icons.Default.Edit
        } else {
          Icons.Default.Check
        }
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (currentlyEditedCharacter?.key != uiCharacter.key) {
            Text(
              modifier = Modifier.padding(horizontal = 5.dp),
              text = uiCharacter.initiative?.toString() ?: "",
            )
          } else {
            TextField(
              modifier =
                Modifier.width(70.dp)
                  .padding(horizontal = 5.dp)
                  .then(
                    if (uiCharacter.initiative == null && uiCharacter.name != null)
                      Modifier.focusRequester(focusRequester)
                    else Modifier
                  ),
              singleLine = true,
              value = uiCharacter.initiative?.toString() ?: "",
              enabled = uiCharacter.notPlayedYet,
              onValueChange = { actions.editInitiative(uiCharacter.key, it) },
              keyboardOptions =
                KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Decimal),
              keyboardActions =
                KeyboardActions(
                  onDone = { actions.toggleEditCharacter(uiCharacter.key) },
                  onPrevious = { focusManager.moveFocus(FocusDirection.Previous) },
                ),
              label = { Text(stringResource(Res.string.initiative_short)) },
            )
          }
          if (shownView == ShownView.CHARACTERS) {
            IconButton(onClick = { actions.toggleEditCharacter(uiCharacter.key) }) {
              AnimatedContent(targetState = toggleEditIcon) {
                Icon(it, contentDescription = "Toggle Edit")
              }
            }
            IconButton(onClick = { actions.deleteCharacter(uiCharacter.key) }) {
              Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
          } else {
            if (uiCharacter.playerCharacter == true) {
              IconButton(enabled = !isGreyed, onClick = { actions.die(uiCharacter.key) }) {
                Icon(
                  imageVector = deathIcon,
                  tint = Color.Black,
                  contentDescription = "Get dying condition",
                )
              }
            } else {
              IconButton(
                enabled = !isGreyed,
                onClick = { actions.deleteCharacter(uiCharacter.key) },
              ) {
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
fun ShowPlayerVsNonPlayerCharacter(
  shownView: ShownView,
  uiCharacter: UiCharacter,
  actions: Actions,
) {
  val isPlayerCharacter = uiCharacter.playerCharacter == true
  if (shownView == ShownView.CHARACTERS) {
    Button(
      modifier = Modifier.padding(start = 10.dp),
      onClick = { actions.togglePlayerCharacter(uiCharacter.key, !isPlayerCharacter) },
    ) {
      Text(
        text =
          stringResource(
            if (isPlayerCharacter) Res.string.shortPlayerCharacter
            else Res.string.shortNonPlayerCharacter
          )
      )
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
  currentlyEditedCharacter: CurrentlyEditedCharacter?,
) {
  LazyColumn(
    state = listState,
    modifier =
      with(columnScope) {
        Modifier.fillMaxWidth()
          .weight(1f)
          .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
      },
  ) {
    items(uiCharacters, key = { it.key }) { character ->
      Box(modifier = Modifier.animateItem()) {
        ShowCharacter(
          character,
          currentlyEditedCharacter,
          isActive = false,
          actions,
          ShownView.CHARACTERS,
          false,
        )
      }
    }
  }
}

@Composable
fun ListConflictTurns(
  columnScope: ColumnScope,
  hasConflict: Boolean,
  showActionList: () -> Unit,
  content: @Composable () -> Unit,
) {
  Box(modifier = with(columnScope) { Modifier.fillMaxWidth().weight(1f) }) {
    if (hasConflict) {
      ExtendedFloatingActionButton(
        modifier = Modifier.align(Alignment.BottomEnd).padding(all = 20.dp),
        onClick = { showActionList() },
      ) {
        Text(stringResource(Res.string.resolveConflicts))
      }
    }
    Box(
      modifier = if (hasConflict) Modifier.background(Color.Transparent).blur(4.dp) else Modifier
    ) {
      content()
    }
  }
}

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

      val alpha =
        if (currentAddTurn <= 0) 1.0f
        else {
          0.5f - 0.5f * (currentHeight - oneRoundHeight) / (constraints.maxHeight - oneRoundHeight)
        }

      val currentAddTurnCopy = currentAddTurn
      val s =
        subcompose(slotId = slotKey) {
          val actualAlpha by
            animateFloatAsState(
              alpha,
              finishedListener = {
                Napier.i("animation for $slotKey finished at $it (goal: $alpha)")
              },
            )

          var targetOffset by remember { mutableStateOf(0) }
          var anim by remember { mutableStateOf<Animatable<Int, AnimationVector1D>?>(null) }
          val scope = rememberCoroutineScope()

          Box(
            modifier =
              Modifier.onPlaced { layoutCoordinates ->
                  targetOffset = layoutCoordinates.positionInParent().y.roundToInt()
                }
                .offset {
                  val animatable =
                    anim ?: Animatable(targetOffset, Int.VectorConverter).also { anim = it }
                  if (animatable.targetValue != targetOffset) {
                    scope.launch { animatable.animateTo(targetOffset) }
                  }
                  IntOffset(0, animatable.value - targetOffset)
                }
          ) {
            Box(modifier = Modifier.alpha(actualAlpha)) {
              val isActive =
                active == currentCharacter.key && currentAddTurnCopy == 0 && anim?.isRunning != true
              ShowCharacter(
                currentCharacter.copy(
                  turn = currentTurn,
                  isDelayed = currentCharacter.isDelayed && currentAddTurnCopy == 0,
                ),
                null,
                isActive = isActive,
                actions,
                ShownView.TURNS,
                isGreyed = currentAddTurnCopy >= 1,
              )
            }
          }
        }
      val measured =
        s[0].measure(
          constraints =
            Constraints(
              minWidth = constraints.minWidth,
              maxWidth = constraints.maxWidth,
              minHeight = 0,
              maxHeight = constraints.maxHeight - currentHeight,
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
      placeables.forEach { it.second.placeRelative(0, it.first) }
    }
  }
}

@Composable
fun InitOrder(
  columnScope: ColumnScope,
  uiCharacters: List<UiCharacter>,
  currentlyEditedCharacter: CurrentlyEditedCharacter?,
  active: String?,
  actions: Actions,
  listState: LazyListState,
  shownView: ShownView,
  hasConflict: Boolean,
  showActionList: () -> Unit,
) {
  if (shownView == ShownView.CHARACTERS) {
    ListCharacters(columnScope, uiCharacters, actions, listState, currentlyEditedCharacter)
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

enum class Screens(val title: StringResource) {
  MainScreen(Res.string.InitiativeTracker),
  ListActions(Res.string.ListActions),
  ConnectionSettings(Res.string.ConnectionSettings),
}

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class,
  ExperimentalComposeUiApi::class,
)
@Composable
@Preview
fun App(data: String? = null) {
  val globalCoroutineScope = rememberCoroutineScope()
  val model = viewModel { Model(Repository(State()), null, data) }
  LaunchedEffect(Unit) {
    val predefinedServerHost =
      data?.split("&")?.firstOrNull { it.startsWith("server=") }?.let { it.substring(7) }
    if (predefinedServerHost != null) {
      ClientConsumer.changeHost(predefinedServerHost)
      ClientConsumer.start(model, globalCoroutineScope)
    }
  }
  val uiState by
    model.state.collectAsState(
      UiState(
        turnConflicts = false,
        shownView = ShownView.CHARACTERS,
        knownPlayerCharacters = listOf(null),
      )
    )

  MaterialTheme {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val pagerState =
      rememberPagerState(initialPage = uiState.shownView.ordinal) { ShownView.entries.size }

    LaunchedEffect(pagerState.currentPage) {
      pagerState.interactionSource
      model.showView(ShownView.entries[pagerState.currentPage])
    }
    LaunchedEffect(uiState.shownView) { pagerState.animateScrollToPage(uiState.shownView.ordinal) }

    val snackBarHostState = remember { SnackbarHostState() }

    var floatingActionMenuExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(floatingActionMenuExpanded) { floatingActionMenuExpanded = false }

    ModalNavigationDrawer(
      drawerState = drawerState,
      drawerContent = {
        ModalDrawerSheet {
          Text(
            stringResource(Res.string.InitiativeTracker),
            Modifier.padding(16.dp)
              .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
          )
          HorizontalDivider()
          NavigationDrawerItem(
            modifier =
              Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            label = { Text(stringResource(Res.string.ConnectionSettings)) },
            badge = { ConnectionState() },
            selected = backStackEntry?.destination?.route == Screens.ConnectionSettings.name,
            onClick = {
              navController.navigate(Screens.ConnectionSettings.name) {
                popUpTo(Screens.MainScreen.name)
                launchSingleTop = true
              }
              scope.launch { drawerState.close() }
            },
          )
          NavigationDrawerItem(
            modifier =
              Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            label = { Text(stringResource(Res.string.Characters)) },
            selected =
              backStackEntry?.destination?.route == Screens.MainScreen.name &&
                uiState.shownView == ShownView.CHARACTERS,
            onClick = {
              navController.navigate(Screens.MainScreen.name) {
                popUpTo(Screens.MainScreen.name)
                launchSingleTop = true
              }
              model.showView(ShownView.CHARACTERS)
              scope.launch { drawerState.close() }
            },
          )
          NavigationDrawerItem(
            modifier =
              Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            label = { Text(stringResource(Res.string.Turns)) },
            selected =
              backStackEntry?.destination?.route == Screens.MainScreen.name &&
                uiState.shownView == ShownView.TURNS,
            onClick = {
              navController.navigate(Screens.MainScreen.name) {
                popUpTo(Screens.MainScreen.name)
                launchSingleTop = true
              }
              model.showView(ShownView.TURNS)
              scope.launch { drawerState.close() }
            },
          )
          HorizontalDivider()
          NavigationDrawerItem(
            modifier =
              Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            label = { Text(stringResource(Res.string.ListActions)) },
            selected = backStackEntry?.destination?.route == Screens.ListActions.name,
            onClick = {
              navController.navigate(Screens.ListActions.name) {
                popUpTo(Screens.MainScreen.name)
                launchSingleTop = true
              }
              scope.launch { drawerState.close() }
            },
          )
          Spacer(modifier = Modifier.weight(1f))
          Row(
            modifier =
              Modifier.fillMaxWidth().align(Alignment.CenterHorizontally).padding(all = 10.dp)
          ) {
            Column(
              modifier =
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            ) {
              Text(
                "${uiState.actions.filterIsInstance<TurnAction.StartTurn>().size} turns played so far in current encounter"
              )
              Text("${uiState.characters.filter { !it.dead }.size} characters still alive")
            }
          }
        }
      },
    ) {
      Scaffold(
        snackbarHost = {
          SnackbarHost(hostState = snackBarHostState)

          Napier.i("hello from Napier")

          LaunchedEffect(Unit) {
            ClientConsumer.clientStatus.collect {
              when (it.status) {
                is ClientStatusState.ConnectionError -> {
                  Napier.i("want to show snackbar ")
                  snackBarHostState.showSnackbar(
                    "Client disconnected: " + it.status.errorMessage,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
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
              val currentScreen =
                Screens.valueOf(backStackEntry?.destination?.route ?: Screens.MainScreen.name)
              Text(
                stringResource(currentScreen.title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            actions = {
              ConnectionState()
              val serverStatus = getPlatform().serverStatus()
              val clientStatus by ClientConsumer.clientStatus.collectAsState()
              val context = getPlatform().getContext()
              IconButton(
                enabled =
                  serverStatus.isRunning && serverStatus.joinLinks.isNotEmpty() ||
                    !serverStatus.isRunning && clientStatus.status is ClientStatusState.Running,
                onClick = {
                  val links =
                    if (serverStatus.isRunning && serverStatus.joinLinks.isNotEmpty()) {
                      Pair(serverStatus.joinLinks[0], serverStatus.joinLinks)
                    } else {
                      Pair(JoinLink(clientStatus.host), listOf())
                    }
                  getPlatform().shareLink(context, links.first, links.second)
                },
              ) {
                Icon(Icons.Default.Share, contentDescription = "Share join link")
              }
            },
            navigationIcon = {
              IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
              }
            },
          )
        },
        floatingActionButton = {
          var playersToAddList by remember { mutableStateOf<List<String?>?>(null) }
          LaunchedEffect(floatingActionMenuExpanded) {
            if (floatingActionMenuExpanded) {
              playersToAddList =
                uiState.knownPlayerCharacters.filter { name ->
                  !uiState.characters.any {
                    it.playerCharacter == true && !it.dead && it.name?.asString() == name
                  }
                }
            } else {
              playersToAddList = null
            }
          }
          val visible =
            backStackEntry?.destination?.route == Screens.MainScreen.name &&
              pagerState.currentPage == ShownView.CHARACTERS.ordinal
          val visibleState = remember { MutableTransitionState(visible) }
          LaunchedEffect(visible) { visibleState.targetState = visible }
          AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(tween(300)) + fadeIn(tween(300)),
            exit = scaleOut(tween(300)) + fadeOut(tween(300)),
          ) {
            FloatingActionButtonMenu(
              expanded = floatingActionMenuExpanded,
              button = {
                ExtendedFloatingActionButton(
                  onClick = {
                    if (!floatingActionMenuExpanded) {
                      floatingActionMenuExpanded = true
                    } else {
                      model.addCharacter(playerCharacter = false)
                      floatingActionMenuExpanded = false
                    }
                  },
                  text = {
                    if (!floatingActionMenuExpanded) {
                      Text("Add character")
                    } else {
                      Text("Add NPC")
                    }
                  },
                  icon = {
                    Icon(
                      imageVector = Icons.Default.AddCircle,
                      // tint = Color.Green,
                      // modifier = Modifier.size(40.dp),
                      contentDescription = "Add character",
                    )
                  },
                )
              },
            ) {
              playersToAddList?.forEach { name ->
                key(name) {
                  FloatingActionButtonMenuItem(
                    onClick = {
                      model.addCharacter(playerCharacter = true, name)
                      floatingActionMenuExpanded = false
                    },
                    text = { Text(name ?: "Add PC") },
                    icon = {
                      Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add Player Character",
                      )
                    },
                  )
                }
              }
            }
          }
        },
      ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screens.MainScreen.name) {
          composable(route = Screens.MainScreen.name) {
            MainScreen(innerPadding, uiState, model, pagerState) {
              navController.navigate(Screens.ListActions.name) {
                popUpTo(Screens.MainScreen.name)
                launchSingleTop = true
              }
            }
          }

          composable(route = Screens.ListActions.name) { ListActions(innerPadding, uiState, model) }

          composable(route = Screens.ConnectionSettings.name) {
            ConnectionSettings(innerPadding, model, globalCoroutineScope)
          }
        }

        val alphaState = remember { MutableTransitionState(0.0f) }
        val alphaTransition = rememberTransition(alphaState)
        LaunchedEffect(floatingActionMenuExpanded) {
          alphaState.targetState = if (floatingActionMenuExpanded) 0.15f else 0.0f
        }

        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(Color.Black.copy(alpha = alphaTransition.currentState))
              .then(
                if (floatingActionMenuExpanded)
                  Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                      while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.changedToUp() }) {
                          floatingActionMenuExpanded = false
                        }
                      }
                    }
                  }
                else Modifier
              )
        )
      }
    }
  }
}

@Composable
fun ConnectionState(
  modifier: Modifier = Modifier,
  transform: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
  val clientStatus by ClientConsumer.clientStatus.collectAsState()
  val serverStatus = getPlatform().serverStatus()
  BadgedBox(
    modifier =
      modifier.then(
        Modifier.clickable(
          enabled = getPlatform().connectionStateClickableEnabled(),
          onClick = getPlatform().connectionStateOnClick(),
        )
      ),
    badge = {
      if (serverStatus.isRunning) {
        Badge { Text(serverStatus.connections.toString()) }
      }
    },
  ) {
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

private fun prettyClock(remote: VectorClock, here: VectorClock): String {
  val count = remote.clock.values.sum()
  val behind =
    remote.clock
      .mapValues { (k, v) ->
        val vv = here.clock[k] ?: 0
        if (v >= vv) v - vv else 0
      }
      .values
      .sum()
  val ahead =
    here.clock
      .mapValues { (k, v) ->
        val vv = remote.clock[k] ?: 0
        if (v >= vv) v - vv else 0
      }
      .values
      .sum()
  return "$count versions, $ahead ahead, $behind behind"
}

@Composable
fun ServerConnectionSettings(m: Model) {
  val serverStatus = getPlatform().serverStatus()
  val vc by m.repository.version.collectAsState()

  ListItem(headlineContent = { Text(serverStatus.message) })
  HorizontalDivider()
  for (connectedClient in serverStatus.discoveredClients) {
    val connectionState =
      @Composable {
        Icon(
          imageVector = vectorResource(Res.drawable.baseline_sync_24),
          contentDescription = "Synced",
        )
      }

    val stateInfo = connectedClient.state?.let { listOf(prettyClock(it, vc)) } ?: listOf()

    val additional =
      stateInfo +
        ((if (connectedClient.connectedViaServer) listOf("server connection") else listOf()) +
          (if (connectedClient.connectedViaClient) listOf("manually connected") else listOf()) +
          (if (connectedClient.connectedViaWifiAware) listOf("nearby device") else listOf())) +
        connectedClient.errorMsg.orEmpty()

    key(connectedClient.id?.name) {
      ListItem(
        headlineContent = { Text(connectedClient.id?.name ?: "") },
        trailingContent = { connectionState() },
        supportingContent = {
          val text = additional.joinToString("\n")
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

  Column(
    modifier =
      Modifier.padding(innerPadding)
        .windowInsetsPadding(
          WindowInsets.ime.only(WindowInsetsSides.Bottom).union(WindowInsets.safeDrawing)
        )
        .verticalScroll(scrollState)
  ) {
    val serverStatus = getPlatform().serverStatus()
    val clientStatus by ClientConsumer.clientStatus.collectAsState()
    val context = getPlatform().getContext()
    getPlatform().ServerSettings()
    ServerConnectionSettings(model)
    HorizontalDivider()
    ListItem(
      headlineContent = { Text("Connect manually") },
      trailingContent = {
        Switch(
          checked = clientStatus.status is ClientStatusState.Running,
          enabled = clientStatus.status !is ClientStatusState.Starting,
          onCheckedChange = { ClientConsumer.toggleClient(model, coroutineScope) },
        )
      },
    )
    ListItem(
      headlineContent = {
        if (
          clientStatus.status is ClientStatusState.Running ||
            clientStatus.status is ClientStatusState.Starting
        ) {
          Text("Host: ${clientStatus.host}")
        } else {
          TextField(
            value = clientStatus.host,
            label = { Text("Host") },
            singleLine = true,
            onValueChange = { ClientConsumer.changeHost(it) },
          )
        }
      }
    )
    ListItem(
      headlineContent = { Text("Client status") },
      supportingContent = {
        Text(
          when (val status = clientStatus.status) {
            is ClientStatusState.ConnectionError -> "Connection error: ${status.errorMessage}"
            is ClientStatusState.Running ->
              "Running (${status.receivedSuccesfulFrames} updates received so far)"
            is ClientStatusState.Starting -> "Starting"
            is ClientStatusState.Stopped -> "Stopped"
          }
        )
      },
    )
    serverStatus.joinLinks.forEach { joinLink ->
      ListItem(
        headlineContent = { Text("Join Link") },
        supportingContent = { Text(joinLink.toUrl()) },
        modifier =
          Modifier.clickable { getPlatform().shareLink(context, joinLink, serverStatus.joinLinks) },
      )
    }
    HorizontalDivider()
    getPlatform().ServerSettingsBelow()
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  innerPadding: PaddingValues,
  uiState: UiState,
  model: Model,
  pagerState: PagerState,
  showActionList: () -> Unit,
) {
  val actions: Actions = model

  Column(Modifier.padding(innerPadding)) {
    PrimaryTabRow(selectedTabIndex = uiState.shownView.ordinal) {
      Tab(
        selected = uiState.shownView == ShownView.CHARACTERS,
        onClick = { actions.showView(ShownView.CHARACTERS) },
        text = { Text("Characters") },
      )
      Tab(
        selected = uiState.shownView == ShownView.TURNS,
        onClick = {
          actions.showView(ShownView.TURNS)
          // TODO Unset edit character
        },
        text = { Text("Turns") },
      )
    }
    HorizontalPager(pagerState) { page ->
      val thisShownView = ShownView.entries[page]
      Column {
        val listState = rememberLazyListState()
        InitOrder(
          this,
          uiState.characters,
          uiState.currentlyEditedCharacter,
          uiState.currentlySelectedCharacter,
          actions,
          listState,
          thisShownView,
          uiState.turnConflicts,
          showActionList,
        )
        if (thisShownView == ShownView.TURNS) {
          val ongoingTurn = uiState.currentlySelectedCharacter != null
          BottomAppBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceAround,
            ) {
              Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                  modifier = Modifier.align(Alignment.Center),
                  enabled = uiState.actions.any { it.third != TurnAction.ResolveConflicts },
                  onClick = {
                    model.pickAction(
                      uiState.actions.let { actions ->
                        val index =
                          actions.indexOfLast { it.third != TurnAction.ResolveConflicts } - 1
                        actions.getOrNull(index)?.first
                      }
                    )
                  },
                ) {
                  Text("Undo")
                }
              }

              Box(modifier = Modifier.weight(1f)) {
                if (ongoingTurn) {
                  Button(
                    modifier = Modifier.align(Alignment.Center),
                    enabled = !uiState.turnConflicts,
                    onClick = {
                      uiState.currentlySelectedCharacter.let {
                        if (it != null) model.finishTurn(it)
                      }
                    },
                  ) {
                    Text("Finish")
                  }
                } else {
                  Button(
                    modifier = Modifier.align(Alignment.Center),
                    enabled = !uiState.turnConflicts,
                    onClick = { model.next() },
                  ) {
                    Text("Start")
                  }
                }
              }

              Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                  modifier = Modifier.align(Alignment.Center),
                  enabled = !uiState.turnConflicts && ongoingTurn,
                  onClick = { model.delay() },
                ) {
                  Text("Delay")
                }
              }
            }
          }
        } else {
          var showDialog by remember { mutableStateOf(false) }
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 5.dp),
            horizontalArrangement = Arrangement.Start,
          ) {
            OutlinedButton(onClick = { showDialog = true }) { Text("Start new encounter") }
          }
          if (showDialog) {
            AlertDialog(
              onDismissRequest = { showDialog = false },
              title = { Text("Restart encounter") },
              text = { Text("This action deletes current turn order and initiatives values") },
              confirmButton = {
                TextButton(
                  onClick = {
                    showDialog = false
                    val currentPlayers =
                      uiState.characters
                        .filter { it.playerCharacter == true && !it.dead }
                        .mapNotNull { it.name?.asString() }
                    model.addPlayerCharacters(currentPlayers)
                    model.restartEncounter()
                  }
                ) {
                  Text("Proceed")
                }
              },
              dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
            )
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
  LazyColumn(
    contentPadding = innerPadding,
    modifier =
      Modifier.windowInsetsPadding(
        WindowInsets.safeDrawing.union(WindowInsets.ime.only(WindowInsetsSides.Bottom))
      ),
  ) {
    items(
      uiState.actions.reversed(),
      key = { it.first.clientIdentifier.name + "-" + it.first.position },
    ) { item ->
      Row(modifier = Modifier.clickable(onClick = { showModalDialogOfDot = item.first })) {
        Text(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
          text = descriptionOfAction(uiState, item),
        )
      }
    }
  }
  val modelDialogVersion = showModalDialogOfDot
  if (modelDialogVersion != null) {
    BasicAlertDialog(
      onDismissRequest = { showModalDialogOfDot = null },
      content = {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
          Column {
            Text(
              modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
              textAlign = null,
              text = "Delete actions menu",
            )
            HorizontalDivider()
            Text(
              text =
                descriptionOfAction(
                  uiState,
                  uiState.actions.find { it.first == modelDialogVersion }!!,
                ),
              modifier = Modifier.padding(16.dp),
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
      },
    )
  }
}

fun descriptionOfAction(uiState: UiState, action: Triple<Dot, ConflictState, TurnAction>): String {
  val conflictStateString =
    when (val af = action.second) {
      ConflictState.InAllTimelines -> ""
      is ConflictState.InTimelines -> "in timelines ${af.timeline.joinToString { it.toString()}}: "
    }

  val result =
    when (val a = action.third) {
      is TurnAction.StartTurn -> {
        val name =
          uiState.characters.find { a.characterId == CharacterId(it.key) }?.name?.asString()
        "$name started turn"
      }
      is TurnAction.Delay -> {
        val name =
          uiState.characters.find { a.characterId == CharacterId(it.key) }?.name?.asString()
        "$name delayed turn"
      }
      is TurnAction.FinishTurn -> {
        val name =
          uiState.characters.find { a.characterId == CharacterId(it.key) }?.name?.asString()
        "$name finished turn"
      }
      is TurnAction.Die -> {
        val name =
          uiState.characters.find { a.characterId == CharacterId(it.key) }?.name?.asString()
        "$name died"
      }
      is TurnAction.ResolveConflicts -> {
        "Actions undone"
      }
    }

  return conflictStateString + result
}
