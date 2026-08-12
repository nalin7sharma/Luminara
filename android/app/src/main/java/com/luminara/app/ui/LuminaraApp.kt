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
import com.luminara.app.ui.screens.AuthScreen
import com.luminara.app.ui.screens.ClassDetailScreen
import com.luminara.app.ui.screens.ClassesScreen
import com.luminara.app.ui.screens.DetailTab
import com.luminara.app.ui.screens.HomeScreen
import com.luminara.app.ui.screens.LectureDetailScreen
import com.luminara.app.ui.screens.LiveScreen
import com.luminara.app.ui.screens.OnboardingScreen
import com.luminara.app.ui.screens.ProcessingScreen
import com.luminara.app.ui.screens.SetupScreen
import com.luminara.app.ui.screens.UploadLectureScreen
import com.luminara.app.viewmodel.LuminaraViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val HOME = "home"
    const val SETUP = "setup"
    const val PROCESSING = "processing"
    const val LIVE = "live"
    const val DETAIL = "detail"
    const val CLASSES = "classes"
    const val CLASS_DETAIL = "class"
    const val UPLOAD = "upload"

    fun detail(tab: String = DetailTab.OVERVIEW.key) = "$DETAIL/$tab"
}

@Composable
fun LuminaraApp(vm: LuminaraViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Evaluated once, deliberately. Keying this on `onboarded` rebuilds the nav
    // graph the moment onboarding completes, which resets the back stack to the
    // new start destination and swallowed the navigation to the auth screen.
    val start = remember {
        if (state.onboarded) Routes.HOME else Routes.ONBOARDING
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
            toast("No app on this device can open a ${if (pack.isPdf) "PDF" else "web page"}.")
        }
    }

    fun goHome() = nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                initialLanguage = state.language,
                initialName = state.displayName,
                initialRole = state.role,
                onContinue = { name, role, code ->
                    vm.completeOnboarding(name, role, code)
                    nav.navigate(Routes.AUTH) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.AUTH) {
            AuthScreen(
                state = state,
                onRegister = { email, password -> vm.register(email, password) { goHome() } },
                onLogin = { email, password -> vm.login(email, password) { goHome() } },
                onSkip = { goHome() },
                onDismissError = vm::dismissAuthError,
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
                onLiveLecture = { nav.navigate(Routes.LIVE) },
                onLanguage = vm::setLanguage,
                onRefresh = vm::refresh,
                onBaseUrlChange = vm::setBaseUrl,
                onClasses = { nav.navigate(Routes.CLASSES) },
                onOpenClass = { id ->
                    vm.openClass(id)
                    nav.navigate(Routes.CLASS_DETAIL)
                },
                onUploadLecture = { nav.navigate(Routes.UPLOAD) },
                onSignIn = { nav.navigate(Routes.AUTH) },
                onSignOut = vm::signOut,
            )
        }

        composable(Routes.CLASSES) {
            ClassesScreen(
                state = state,
                onOpenClass = { id ->
                    vm.openClass(id)
                    nav.navigate(Routes.CLASS_DETAIL)
                },
                onCreate = { name, subject ->
                    vm.createClass(name, subject) { created ->
                        vm.openClass(created.id)
                        nav.navigate(Routes.CLASS_DETAIL)
                    }
                },
                onJoin = { code ->
                    vm.joinClass(code) { joined ->
                        vm.openClass(joined.id)
                        nav.navigate(Routes.CLASS_DETAIL)
                    }
                },
                onDismissError = vm::dismissClassError,
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.CLASS_DETAIL) {
            ClassDetailScreen(
                state = state,
                onOpenLecture = { id ->
                    vm.loadLecture(id)
                    nav.navigate(Routes.detail())
                },
                onUpload = { nav.navigate(Routes.UPLOAD) },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.UPLOAD) {
            UploadLectureScreen(
                state = state,
                initialClassId = state.classDetail?.schoolClass?.id,
                onUpload = { title, classId, audio, image ->
                    vm.uploadLecture(
                        title = title,
                        classId = classId,
                        audio = audio,
                        image = image,
                        onProcessing = {
                            nav.navigate(Routes.PROCESSING) {
                                popUpTo(Routes.UPLOAD) { inclusive = true }
                            }
                        },
                        onFailed = { message -> toast(message) },
                    )
                },
                onBack = { nav.popBackStack() },
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
                onBack = { goHome() },
            )
        }

        composable(Routes.LIVE) {
            LiveScreen(
                state = state,
                onStart = { vm.startLive { message -> toast(message) } },
                onTogglePause = vm::togglePauseLive,
                onEnd = {
                    vm.endLive(
                        onReady = { id ->
                            vm.loadLecture(id)
                            nav.navigate(Routes.detail()) {
                                popUpTo(Routes.LIVE) { inclusive = true }
                            }
                        },
                        onFailed = { message -> toast(message) },
                    )
                },
                onLeave = vm::abandonLive,
                onBack = { nav.popBackStack() },
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
                onBack = { nav.popBackStack() },
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
                onPublish = vm::setPublished,
            )
        }
    }
}
