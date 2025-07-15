package com.example.message.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ConversationItem(
    val uid: String,
    val lastTimestamp: Long,
    val unreadCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(navController: NavController) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance().reference

    var conversations by remember { mutableStateOf(listOf<ConversationItem>()) }
    var usernames by remember { mutableStateOf(mapOf<String, String>()) }
    var selectedFilter by remember { mutableStateOf("All") }


    var expandedMenuIndex by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<ConversationItem?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun deleteConversation(item: ConversationItem) {
        val convId = listOf(currentUserId, item.uid).sorted().joinToString("_")
        database.child("messages").child(convId).removeValue()
        database.child("conversations").child(currentUserId).child(item.uid).removeValue()
        database.child("conversations").child(item.uid).child(currentUserId).removeValue()
        conversations = conversations.filter { it.uid != item.uid }

        coroutineScope.launch {
            snackbarHostState.showSnackbar("Conversation supprimée")
        }
    }

    LaunchedEffect(Unit) {
        val msgRef = database.child("messages")
        msgRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val convList = mutableListOf<ConversationItem>()

                for (conversation in snapshot.children) {
                    var lastTimestamp = 0L
                    var otherUser: String? = null
                    var unreadCount = 0

                    for (messageSnap in conversation.children) {
                        val from = messageSnap.child("senderId").getValue(String::class.java)
                        val to = messageSnap.child("receiverId").getValue(String::class.java)
                        val timestamp = messageSnap.child("timestamp").getValue(Long::class.java) ?: 0L
                        val isRead = messageSnap.child("read").getValue(Boolean::class.java) ?: true

                        if (from == currentUserId || to == currentUserId) {
                            if (timestamp > lastTimestamp) lastTimestamp = timestamp
                            val other = if (from == currentUserId) to else from
                            if (other != null) otherUser = other
                            if (!isRead && to == currentUserId) unreadCount++
                        }
                    }

                    if (otherUser != null) {
                        convList.add(ConversationItem(otherUser, lastTimestamp, unreadCount))
                    }
                }

                conversations = convList.sortedByDescending { it.lastTimestamp }

                convList.map { it.uid }.distinct().forEach { uid ->
                    if (!usernames.containsKey(uid)) {
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
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // 🔁 CONVERSATIONS
    LaunchedEffect(Unit) {
        val convRef = database.child("conversations").child(currentUserId)
        convRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val uids = snapshot.children.mapNotNull { it.key }.toSet()

                for (uid in uids) {
                    if (!conversations.any { it.uid == uid }) {
                        conversations = conversations + ConversationItem(uid, 0L, 0)
                    }

                    if (!usernames.containsKey(uid)) {
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
                        Text("Déconnexion", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                listOf("All", "Unread").forEach { label ->
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

            val filtered = conversations.filter {
                when (selectedFilter) {
                    "All" -> true
                    "Unread" -> it.unreadCount > 0
                    else -> true
                }
            }

            // Liste des conversations
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered.size) { index ->
                    val item = filtered[index]
                    val username = usernames[item.uid] ?: "Chargement..."
                    val formattedTime = remember(item.lastTimestamp) {
                        val sdf = SimpleDateFormat("HH:mm | dd/MM/yyyy", Locale.getDefault())
                        sdf.format(Date(item.lastTimestamp))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { navController.navigate("message/${item.uid}") },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
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
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0E0E0))
                                    .padding(6.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = username,
                                    fontSize = 18.sp,
                                    fontWeight = if (item.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = formattedTime,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }

                            if (item.unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Red, shape = CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Box {
                                IconButton(onClick = {
                                    expandedMenuIndex = if (expandedMenuIndex == index) null else index
                                }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                                }

                                DropdownMenu(
                                    expanded = expandedMenuIndex == index,
                                    onDismissRequest = { expandedMenuIndex = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Supprimer") },
                                        onClick = {
                                            conversationToDelete = item
                                            showDialog = true
                                            expandedMenuIndex = null
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showDialog && conversationToDelete != null) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Confirmation") },
                    text = {
                        Text("Voulez-vous vraiment supprimer la conversation avec \"${usernames[conversationToDelete!!.uid] ?: "cet utilisateur"}\" ?")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteConversation(conversationToDelete!!)
                            showDialog = false
                        }) {
                            Text("Supprimer", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Annuler")
                        }
                    }
                )
            }
        }
    }
}
