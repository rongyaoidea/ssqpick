package com.ssqpick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ssqpick.designsystem.theme.SsqTheme
import com.ssqpick.feature.check.CheckScreen
import com.ssqpick.feature.check.CheckViewModel
import com.ssqpick.feature.machine.MachineScreen
import com.ssqpick.feature.machine.MachineViewModel
import com.ssqpick.feature.ticket.TicketScreen
import com.ssqpick.feature.ticket.TicketViewModel
import org.koin.compose.viewmodel.koinViewModel

/** 三个顶层页。 */
private enum class TopTab(val route: String, val label: String) {
    MACHINE("machine", "摇号"),
    TICKET("ticket", "选号"),
    CHECK("check", "对奖"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SsqTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { AppScaffold() }
            }
        }
    }
}

@Composable
private fun AppScaffold() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopTab.entries.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    TopTab.MACHINE -> Icons.Filled.Casino
                                    TopTab.TICKET -> Icons.Filled.FormatListNumbered
                                    TopTab.CHECK -> Icons.Filled.EmojiEvents
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopTab.MACHINE.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopTab.MACHINE.route) {
                val vm: MachineViewModel = koinViewModel()
                MachineScreen(viewModel = vm)
            }
            composable(TopTab.TICKET.route) {
                val vm: TicketViewModel = koinViewModel()
                TicketScreen(viewModel = vm)
            }
            composable(TopTab.CHECK.route) {
                val vm: CheckViewModel = koinViewModel()
                CheckScreen(viewModel = vm)
            }
        }
    }
}
