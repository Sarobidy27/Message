package com.example.message.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@Composable
fun ConversationListScreen(navController: NavController) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance().reference
    var users by remember { mutableStateOf(setOf<String>()) }
    var usernames by remember { mutableStateOf(mapOf<String, String>()) }

    LaunchedEffect(Unit) {
        val msgRef = database.child("messages")
        msgRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userIds = mutableSetOf<String>()
                for (child in snapshot.children) {
                    val from = child.child("from").getValue(String::class.java)
                    val to = child.child("to").getValue(String::class.java)

                    if (from == currentUserId && to != null) userIds.add(to)
                    else if (to == currentUserId && from != null) userIds.add(from)
                }

                users = userIds

                // Charger les noms d'utilisateur à partir du champ "Nom"
                userIds.forEach { uid ->
                    database.child("users").child(uid).child("Nom")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val nom = snapshot.getValue(String::class.java) ?: "Utilisateur inconnu"
                                usernames = usernames + (uid to nom)
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Conversations",
            style = MaterialTheme.typography.titleLarge,
            fontSize = 24.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        users.forEach { uid ->
            val username = usernames[uid] ?: "Chargement..."
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { navController.navigate("message/$uid") },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Text(
                    text = username,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { navController.navigate("new_message") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Nouveau message", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                FirebaseAuth.getInstance().signOut()
                navController.navigate("login") {
                    popUpTo("conversation_list") { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Déconnexion", fontSize = 16.sp)
        }
    }
}
