package com.example.message.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(backStackEntry: NavBackStackEntry) {
    val receiverId = backStackEntry.arguments?.getString("receiverId") ?: return
    val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance().reference
    val conversationId = listOf(senderId, receiverId).sorted().joinToString("_")

    var messages by remember { mutableStateOf(listOf<MessageWithId>()) }
    var newMessage by remember { mutableStateOf("") }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<MessageWithId?>(null) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }

    var expirationDurationSeconds by remember { mutableStateOf(0) }
    var userName by remember { mutableStateOf("...") }
    var menuExpanded by remember { mutableStateOf(false) }
    var ephemeralMenuExpanded by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    LaunchedEffect(receiverId) {
        database.child("users").child(receiverId).child("Nom").get()
            .addOnSuccessListener { snapshot ->
                userName = snapshot.getValue(String::class.java) ?: "Utilisateur"
            }
    }

    LaunchedEffect(Unit) {
        val ref = database.child("messages").child(conversationId)
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<MessageWithId>()
                for (child in snapshot.children) {
                    val msg = child.getValue(Message::class.java)
                    msg?.let {
                        list.add(MessageWithId(it, child.key ?: ""))
                        if (it.receiverId == senderId && it.read == false) {
                            child.ref.child("read").setValue(true)
                        }
                    }
                }
                messages = list.sortedBy { it.message.timestamp }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    //suppression auto
    LaunchedEffect(messages) {
        while (true) {
            delay(3000)
            val now = System.currentTimeMillis()
            messages.forEach { msgWithId ->
                val expiry = msgWithId.message.expiryTimestamp
                if (expiry != null && now > expiry) {
                    database.child("messages").child(conversationId).child(msgWithId.id).removeValue()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Avatar", modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = userName)
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Durée des messages éphémères") },
                            onClick = { ephemeralMenuExpanded = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer la conversation") },
                            onClick = {
                                database.child("messages").child(conversationId).removeValue()
                                menuExpanded = false
                            }
                        )
                    }

                    DropdownMenu(
                        expanded = ephemeralMenuExpanded,
                        onDismissRequest = { ephemeralMenuExpanded = false }
                    ) {
                        listOf(0, 10, 30, 60).forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text(if (seconds == 0) "Permanent" else "$seconds secondes") },
                                onClick = {
                                    expirationDurationSeconds = seconds
                                    ephemeralMenuExpanded = false
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                messages.forEach { msgWithId ->
                    val msg = msgWithId.message
                    val isMe = msg.senderId == senderId
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                    val isSelected = selectedMessageId == msgWithId.id

                    val countdown = msg.expiryTimestamp?.let {
                        val remaining = (it - now) / 1000
                        if (remaining > 0) "$remaining s" else null
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .combinedClickable(
                                onClick = { selectedMessageId = null },
                                onLongClick = { selectedMessageId = msgWithId.id }
                            ),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isMe) Color(0xFFD1E7DD) else Color(0xFFE2E3E5),
                                        shape = MaterialTheme.shapes.large
                                    )
                                    .padding(12.dp)
                                    .widthIn(0.dp, 280.dp)
                            ) {
                                Column {
                                    Text(msg.content, fontSize = 17.sp, color = Color.Black)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(time, fontSize = 12.sp, color = Color.Gray)
                                        if (countdown != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("⏳ $countdown", fontSize = 12.sp, color = Color.Red)
                                        }
                                        if (isMe && msg.read == true) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.Default.DoneAll, contentDescription = "Vu", tint = Color.Blue, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            if (isMe && isSelected) {
                                Row {
                                    TextButton(onClick = {
                                        newMessage = msg.content.replace(" (modifié)", "")
                                        editingMessageId = msgWithId.id
                                        selectedMessageId = null
                                    }) {
                                        Text("Modifier", color = Color.Blue)
                                    }
                                    TextButton(onClick = {
                                        messageToDelete = msgWithId
                                        showDialog = true
                                        selectedMessageId = null
                                    }) {
                                        Text("Supprimer", color = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = newMessage,
                    onValueChange = { newMessage = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Écrire...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newMessage.isNotBlank()) {
                            if (editingMessageId != null) {
                                val msgRef = database.child("messages").child(conversationId).child(editingMessageId!!)
                                msgRef.child("content").setValue(newMessage.trim() + " (modifié)")
                                editingMessageId = null
                            } else {
                                val now = System.currentTimeMillis()
                                val expiry = if (expirationDurationSeconds > 0) now + expirationDurationSeconds * 1000 else null
                                val msg = mapOf(
                                    "senderId" to senderId,
                                    "receiverId" to receiverId,
                                    "content" to newMessage.trim(),
                                    "timestamp" to now,
                                    "read" to false,
                                    "expiryTimestamp" to expiry
                                )
                                database.child("messages").child(conversationId).push().setValue(msg)
                                database.child("conversations").child(senderId).child(receiverId).setValue(true)
                                database.child("conversations").child(receiverId).child(senderId).setValue(true)
                            }
                            newMessage = ""
                        }
                    }
                ) {
                    Text(if (editingMessageId != null) "Modifier" else "Envoyer")
                }
            }

            if (showDialog && messageToDelete != null) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Confirmation") },
                    text = { Text("Voulez-vous vraiment supprimer ce message ?") },
                    confirmButton = {
                        TextButton(onClick = {
                            database.child("messages").child(conversationId).child(messageToDelete!!.id).removeValue()
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

data class MessageWithId(val message: Message, val id: String)

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val read: Boolean? = null,
    val expiryTimestamp: Long? = null
)
