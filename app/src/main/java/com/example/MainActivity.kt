package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.ThemeOption
import com.example.ui.components.AuthDialog
import com.example.ui.components.NavDestination
import com.example.ui.components.OtakuBottomBar
import com.example.ui.components.OtakuTopBar
import com.example.ui.components.routeHasOwnTopInset
import com.example.ui.screens.detail.WorkDetailScreen
import com.example.ui.screens.detail.WorkDetailViewModel
import com.example.ui.screens.explore.ExploreScreen
import com.example.ui.screens.explore.ExploreViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.library.LibraryViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import com.example.ui.screens.profile.ImageCropScreen
import com.example.ui.screens.profile.SettingsScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.profile.ProfileViewModel
import com.example.ui.screens.schedule.ScheduleScreen
import com.example.ui.screens.schedule.ScheduleViewModel
import com.example.ui.theme.OtakuVerseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OtakuVerseApp

        setContent {
            val preferencesRepo = app.preferencesRepository
            val currentTheme by preferencesRepo.selectedTheme.collectAsStateWithLifecycle(initialValue = ThemeOption.NOITE_AZUL)
            val scope = rememberCoroutineScope()

            OtakuVerseTheme(themeOption = currentTheme) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: NavDestination.HOME.route

                val homeViewModel = remember { HomeViewModel(app.workRepository) }
                val exploreViewModel = remember {
                    ExploreViewModel(
                        workRepository = app.workRepository,
                        libraryRepository = app.libraryRepository,
                        preferencesRepository = app.preferencesRepository
                    )
                }
                val scheduleViewModel = remember {
                    ScheduleViewModel(
                        workRepository = app.workRepository,
                        libraryRepository = app.libraryRepository,
                        preferencesRepository = app.preferencesRepository
                    )
                }
                val libraryViewModel = remember {
                    LibraryViewModel(
                        libraryRepository = app.libraryRepository,
                        workRepository = app.workRepository
                    )
                }
                val profileViewModel = remember {
                    ProfileViewModel(
                        application = app,
                        preferencesRepository = app.preferencesRepository,
                        workRepository = app.workRepository,
                        libraryRepository = app.libraryRepository,
                        tursoClient = app.tursoClient,
                        authRepository = app.authRepository,
                        syncRepository = app.syncRepository
                    )
                }

                val authState by app.authRepository.authState.collectAsStateWithLifecycle()
                val syncState by app.syncRepository.syncState.collectAsStateWithLifecycle()
                val userNickname by preferencesRepo.userNickname.collectAsStateWithLifecycle(initialValue = "Otaku")
                val avatarImagePath by preferencesRepo.avatarImagePath.collectAsStateWithLifecycle(initialValue = null)

                var showAuthDialog by rememberSaveable { mutableStateOf(false) }

                val isTopLevelRoute = NavDestination.entries.any { it.route == currentRoute }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = if (routeHasOwnTopInset(currentRoute)) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
                    topBar = {
                        if (currentRoute == NavDestination.HOME.route) {
                            OtakuTopBar(
                                currentRoute = currentRoute,
                                currentTheme = currentTheme,
                                userNickname = userNickname,
                                avatarImagePath = avatarImagePath,
                                isLoggedIn = authState.isUserLoggedIn,
                                syncState = syncState,
                                onSelectTheme = { newTheme ->
                                    scope.launch { preferencesRepo.setTheme(newTheme) }
                                },
                                onAvatarClick = {
                                    navController.navigate(NavDestination.PROFILE.route) {
                                        launchSingleTop = true
                                    }
                                },
                                onLoginClick = { showAuthDialog = true },
                                onSyncClick = { profileViewModel.syncNow() }
                            )
                        }
                    },
                    bottomBar = {
                        if (isTopLevelRoute) {
                            OtakuBottomBar(
                                currentRoute = currentRoute,
                                onNavigate = { destination ->
                                    if (currentRoute != destination.route) {
                                        navController.navigate(destination.route) {
                                            popUpTo(NavDestination.HOME.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NavDestination.HOME.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(NavDestination.HOME.route) {
                            HomeScreen(
                                viewModel = homeViewModel,
                                onWorkClick = { workId ->
                                    navController.navigate("work_detail/$workId")
                                },
                                onViewAllSection = { sectionKey ->
                                    if (sectionKey == "monthSchedule") {
                                        navController.navigate(NavDestination.SCHEDULE.route) {
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.navigate(NavDestination.EXPLORE.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onNavigateToProfile = {
                                    navController.navigate(NavDestination.PROFILE.route) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(NavDestination.EXPLORE.route) {
                            ExploreScreen(
                                viewModel = exploreViewModel,
                                onWorkClick = { workId ->
                                    navController.navigate("work_detail/$workId")
                                },
                                onNavigateToProfile = {
                                    navController.navigate(NavDestination.PROFILE.route) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(NavDestination.SCHEDULE.route) {
                            ScheduleScreen(
                                viewModel = scheduleViewModel,
                                onWorkClick = { workId ->
                                    navController.navigate("work_detail/$workId")
                                },
                                onNavigateToProfile = {
                                    navController.navigate(NavDestination.PROFILE.route) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(NavDestination.LIBRARY.route) {
                            LibraryScreen(
                                viewModel = libraryViewModel,
                                onWorkClick = { workId ->
                                    navController.navigate("work_detail/$workId")
                                },
                                onExploreClick = {
                                    navController.navigate(NavDestination.EXPLORE.route) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(NavDestination.PROFILE.route) {
                            ProfileScreen(
                                viewModel = profileViewModel,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToCrop = {
                                    navController.navigate("image_crop")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = profileViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("image_crop") {
                            ImageCropScreen(
                                viewModel = profileViewModel,
                                onBack = { navController.popBackStack() },
                                onCropSuccess = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "work_detail/{workId}",
                            arguments = listOf(navArgument("workId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val workId = backStackEntry.arguments?.getString("workId").orEmpty()
                            val detailViewModel = remember(workId) {
                                WorkDetailViewModel(
                                    workId = workId,
                                    workRepository = app.workRepository,
                                    libraryRepository = app.libraryRepository,
                                    preferencesRepository = app.preferencesRepository
                                )
                            }
                            WorkDetailScreen(
                                viewModel = detailViewModel,
                                onBack = { navController.popBackStack() },
                                onSimilarWorkClick = { nextId ->
                                    navController.navigate("work_detail/$nextId")
                                }
                            )
                        }
                    }

                    if (showAuthDialog) {
                        AuthDialog(
                            authRepository = app.authRepository,
                            onDismiss = { showAuthDialog = false },
                            onAuthSuccess = { showAuthDialog = false }
                        )
                    }
                }
            }
        }
    }
}
