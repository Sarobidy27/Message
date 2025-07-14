package com.example.message.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(navController: NavController) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance().reference

    var users by remember { mutableStateOf(setOf<String>()) }
    var usernames by remember { mutableStateOf(mapOf<String, String>()) }
    var selectedFilter by remember { mutableStateOf("All") }

    // Simuler des favoris et non lus (à remplacer plus tard par données réelles)
    val favoritesList = listOf("id_user_1") // à adapter
    val unreadMessages = remember { mutableStateOf(setOf<String>()) }

    // Charger les utilisateurs avec qui il y a des messages
    LaunchedEffect(Unit) {
        val msgRef = database.child("messages")
        msgRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userIds = mutableSetOf<String>()
                val unread = mutableSetOf<String>()

                for (child in snapshot.children) {
                    val from = child.child("from").getValue(String::class.java)
                    val to = child.child("to").getValue(String::class.java)
                    val isRead = child.child("read").getValue(Boolean::class.java) ?: true

                    if (from == currentUserId && to != null) userIds.add(to)
                    else if (to == currentUserId && from != null) {
                        userIds.add(from)
                        if (!isRead) unread.add(from)
                    }
                }

                users = userIds
                unreadMessages.value = unread

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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("new_message") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Message, contentDescription = "Nouveau message", tint = Color.White)
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Conversations") },
                actions = {
                    TextButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate("login") {
                            popUpTo("conversation_list") { inclusive = true }
                        }
                    }) {
                        Text("Déconnexion", color = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Filtres
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                listOf("All", "Unread", "Favorites").forEach { label ->
                    val selected = selectedFilter == label
                    Button(
                        onClick = { selectedFilter = label },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.LightGray
                        )
                    ) {
                        Text(label, color = if (selected) Color.White else Color.Black)
                    }
                }
            }

            // Appliquer filtre
            val filteredUsers = users.filter { uid ->
                when (selectedFilter) {
                    "All" -> true
                    "Unread" -> unreadMessages.value.contains(uid)
                    "Favorites" -> favoritesList.contains(uid)
                    else -> true
                }
            }

            // Liste des conversations
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredUsers) { uid ->
                    val username = usernames[uid] ?: "Chargement..."
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { navController.navigate("message/$uid") },
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Avatar",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 12.dp)
                            )
                            Text(
                                text = username,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
