package com.example.message.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AuthScreen(auth: FirebaseAuth) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (auth.currentUser != null) "chat" else "login"
    ) {
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
      //  composable("chat") { ChatScreen(navController) }
    }
}
