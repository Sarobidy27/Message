package com.example.message

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import com.example.message.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController: NavHostController = rememberNavController()

            Surface {
                NavHost(navController = navController, startDestination = "login") {
                    composable("login") { LoginScreen(navController) }
                    composable("register") { RegisterScreen(navController) }
                    composable("conversation_list") { ConversationListScreen(navController) }
                    composable("new_message") { NewMessageScreen(navController) }
                    composable("message/{receiverId}") { backStackEntry ->
                        MessageScreen(backStackEntry)
                    }
                    composable("profile") { ProfileScreen(navController) }
                }
            }
        }
    }
}
