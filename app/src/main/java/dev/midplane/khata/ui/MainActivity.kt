// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.midplane.khata.ui.screens.AddDueScreen
import dev.midplane.khata.ui.screens.ClientDetailScreen
import dev.midplane.khata.ui.screens.ClientsScreen
import dev.midplane.khata.ui.screens.DueDetailScreen
import dev.midplane.khata.ui.screens.DuesScreen
import dev.midplane.khata.ui.screens.OrgsScreen
import dev.midplane.khata.ui.screens.SmsScreen
import dev.midplane.khata.ui.screens.SummaryScreen
import dev.midplane.khata.ui.theme.KhataTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KhataTheme { KhataRoot() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("summary", "Summary", Icons.Outlined.Insights, Icons.Filled.Insights),
    Tab("dues", "Dues", Icons.Outlined.Receipt, Icons.Filled.Receipt),
    Tab("clients", "Clients", Icons.Outlined.People, Icons.Filled.People),
)

/**
 * Pops only if the current screen is fully shown. A second tap on a back/close action while the
 * previous pop is still animating would otherwise pop the start destination and leave a blank screen.
 */
private fun NavHostController.popSafely() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun KhataRoot() {
    val vm: KhataViewModel = viewModel()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        val selected = route == t.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(t.route) },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        val openDue: (Long) -> Unit = { nav.navigate("due/$it") }
        val openClient: (Long) -> Unit = { nav.navigate("client/$it") }
        val addDue: (Long?) -> Unit = { nav.navigate(if (it == null) "add" else "add?clientId=$it") }

        NavHost(nav, startDestination = "summary", modifier = Modifier.padding(pad).consumeWindowInsets(pad)) {
            composable("summary") {
                SummaryScreen(
                    vm,
                    onAddDue = { addDue(null) },
                    onOpenDue = openDue,
                    onOpenClient = openClient,
                    onOpenSms = { nav.navigate("sms") },
                    onShowDues = { filter -> vm.setDueFilter(filter); nav.switchTab("dues") },
                    onManageOrgs = { nav.navigate("orgs") },
                )
            }
            composable("dues") { DuesScreen(vm, onOpenDue = openDue, onAddDue = { addDue(null) }, onManageOrgs = { nav.navigate("orgs") }) }
            composable("clients") { ClientsScreen(vm, onOpenClient = openClient, onAddDue = { addDue(null) }, onManageOrgs = { nav.navigate("orgs") }) }
            composable("orgs") { OrgsScreen(vm, onBack = { nav.popSafely() }) }
            composable(
                "add?clientId={clientId}",
                arguments = listOf(navArgument("clientId") { type = NavType.LongType; defaultValue = -1L }),
            ) { e ->
                val clientId = e.arguments?.getLong("clientId")?.takeIf { it > 0 }
                AddDueScreen(
                    vm, presetClientId = clientId,
                    onBack = { nav.popSafely() },
                    onSaved = { id ->
                        nav.popSafely()
                        nav.navigate("due/$id")
                    },
                )
            }
            composable("due/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                DueDetailScreen(
                    vm, dueId = e.arguments!!.getLong("id"),
                    onBack = { nav.popSafely() },
                    onOpenClient = openClient,
                    onEdit = { nav.navigate("edit/${e.arguments!!.getLong("id")}") },
                )
            }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                AddDueScreen(
                    vm, presetClientId = null,
                    editDueId = e.arguments!!.getLong("id"),
                    onBack = { nav.popSafely() },
                    onSaved = { nav.popSafely() },
                )
            }
            composable("client/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                ClientDetailScreen(
                    vm, clientId = id,
                    onBack = { nav.popSafely() },
                    onOpenDue = openDue,
                    onAddDue = { addDue(id) },
                )
            }
            composable("sms") { SmsScreen(vm, onBack = { nav.popSafely() }, onOpenDue = openDue) }
        }
    }
}
