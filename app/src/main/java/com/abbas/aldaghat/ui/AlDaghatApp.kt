package com.abbas.aldaghat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abbas.aldaghat.di.AppContainer
import com.abbas.aldaghat.ui.folder.FolderScreen
import com.abbas.aldaghat.ui.folder.FolderViewModel
import com.abbas.aldaghat.ui.history.HistoryScreen
import com.abbas.aldaghat.ui.history.HistoryViewModel
import com.abbas.aldaghat.ui.home.HomeScreen
import com.abbas.aldaghat.ui.home.HomeViewModel

private enum class Screen { HOME, HISTORY, FOLDER }

@Composable
fun AlDaghatApp(container: AppContainer) {
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val screen = Screen.entries.firstOrNull { it.name == screenName } ?: Screen.HOME

    BackHandler(enabled = screen != Screen.HOME) { screenName = Screen.HOME.name }

    when (screen) {
        Screen.HOME -> {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
            HomeScreen(
                viewModel = homeViewModel,
                onOpenHistory = { screenName = Screen.HISTORY.name },
                onOpenFolder = { screenName = Screen.FOLDER.name },
            )
        }

        Screen.HISTORY -> {
            val historyViewModel: HistoryViewModel =
                viewModel(factory = HistoryViewModel.factory(container))
            HistoryScreen(
                viewModel = historyViewModel,
                onBack = { screenName = Screen.HOME.name },
                onOpenFolder = { screenName = Screen.FOLDER.name },
            )
        }

        Screen.FOLDER -> {
            val folderViewModel: FolderViewModel =
                viewModel(factory = FolderViewModel.factory(container))
            FolderScreen(
                viewModel = folderViewModel,
                onBack = { screenName = Screen.HOME.name },
            )
        }
    }
}
