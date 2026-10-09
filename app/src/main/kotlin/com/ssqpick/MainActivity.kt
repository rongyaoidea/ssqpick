package com.ssqpick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
private enum class TopTab(val route: String, val label: String, val title: String) {
    MACHINE("machine", "摇号", "3D 摇号机"),
    TICKET("ticket", "选号", "选号"),
    CHECK("check", "对奖", "对奖"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 主题只在这里包一层，feature 屏幕直接消费 MaterialTheme
            SsqTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppScaffold()
                }
            }
        }
    }
}

@Composable
private fun AppScaffold() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val active = TopTab.entries.firstOrNull { tab ->
        current?.hierarchy?.any { it.route == tab.route } == true
    } ?: TopTab.MACHINE

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AppHeader(active) },
        bottomBar = {
            NavigationBar {
                TopTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == active,
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

/** 统一的应用头部：应用名 + 当前页标题 + 一句诚实的副标题。 */
@Composable
private fun AppHeader(active: TopTab) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "双色球选号机",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    active.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                "选号由本地 CSPRNG 完成 · 3D 摇号机只负责演示结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
