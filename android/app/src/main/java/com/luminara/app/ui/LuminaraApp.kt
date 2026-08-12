package com.luminara.app.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luminara.app.data.StudyPackSaver
import com.luminara.app.ui.screens.DetailTab
import com.luminara.app.ui.screens.HomeScreen
import com.luminara.app.ui.screens.LectureDetailScreen
import com.luminara.app.ui.screens.OnboardingScreen
import com.luminara.app.ui.screens.ProcessingScreen
import com.luminara.app.ui.screens.SetupScreen
import com.luminara.app.viewmodel.LuminaraViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETUP = "setup"
    const val PROCESSING = "processing"
    const val DETAIL = "detail"

    fun detail(tab: String = DetailTab.OVERVIEW.key) = "$DETAIL/$tab"
}

@Composable
fun LuminaraApp(vm: LuminaraViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // The welcome flow is shown only until the student has chosen a language;
    // the choice is read from local storage before the first frame.
    val start = remember(state.onboarded) {
        if (state.onboarded) Routes.HOME else Routes.ONBOARDING
    }

    fun launchPack(share: Boolean) {
        val pack = state.savedPack ?: return
        val intent = if (share) {
            StudyPackSaver.shareIntent(pack)
        } else {
            StudyPackSaver.openIntent(pack)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No app on this device can open a ${if (pack.isPdf) "PDF" else "web page"}.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                initialLanguage = state.language,
                onContinue = { code ->
                    vm.completeOnboarding(code)
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onStartLecture = { nav.navigate(Routes.SETUP) },
                onOpenLecture = { id ->
                    vm.loadLecture(id)
                    nav.navigate(Routes.detail())
                },
                onAskBob = { id ->
                    vm.loadLecture(id)
                    nav.navigate(Routes.detail(DetailTab.BOB.key))
                },
                onLanguage = vm::setLanguage,
                onRefresh = vm::refresh,
                onBaseUrlChange = vm::setBaseUrl,
            )
        }

        composable(Routes.SETUP) {
            SetupScreen(
                state = state,
                onLanguage = vm::setLanguage,
                onProcess = { fresh ->
                    vm.startDemo(
                        fresh = fresh,
                        onProcessing = {
                            nav.navigate(Routes.PROCESSING) {
                                popUpTo(Routes.SETUP) { inclusive = true }
                            }
                        },
                        onReady = {
                            nav.navigate(Routes.detail()) {
                                popUpTo(Routes.SETUP) { inclusive = true }
                            }
                        },
                    )
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.PROCESSING) {
            ProcessingScreen(
                state = state,
                onDone = {
                    nav.navigate(Routes.detail()) {
                        popUpTo(Routes.PROCESSING) { inclusive = true }
                    }
                },
                onBack = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                },
            )
        }

        composable(
            route = "${Routes.DETAIL}/{tab}",
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = DetailTab.OVERVIEW.key
                }
            ),
        ) { entry ->
            LectureDetailScreen(
                state = state,
                initialTab = DetailTab.from(entry.arguments?.getString("tab")),
                onBack = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onLanguage = vm::setLanguage,
                onAsk = vm::ask,
                onRetryAsk = vm::retryLast,
                onClearChat = vm::clearChat,
                onDownload = vm::downloadStudyPack,
                onOpenPack = { launchPack(share = false) },
                onSharePack = { launchPack(share = true) },
                onDismissPack = vm::dismissSavedPack,
                onSearchQuery = vm::setSearchQuery,
                onClearSearch = vm::clearSearch,
                onReprocess = { id ->
                    vm.reprocess(id) {
                        nav.navigate(Routes.PROCESSING) {
                            popUpTo("${Routes.DETAIL}/{tab}") { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
