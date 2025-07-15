package com.example.message.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    val coroutineScope = rememberCoroutineScope()

    // 🔁 Charger les messages + marquer lus
    LaunchedEffect(Unit) {
        val ref = database.child("messages").child(conversationId)
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<MessageWithId>()
                for (child in snapshot.children) {
                    val msg = child.getValue(Message::class.java)
                    msg?.let {
                        list.add(MessageWithId(it, child.key ?: ""))
                        // Marquer comme lu
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

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

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
                                Text(
                                    msg.content,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = time,
                                        fontSize = 12.sp,
                                        color = Color.DarkGray,
                                        modifier = Modifier.alpha(0.7f)
                                    )
                                    if (isMe && msg.read == true) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Vu",
                                            tint = Color.Blue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (isSelected && isMe) {
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
            Button(onClick = {
                if (newMessage.isNotBlank()) {
                    if (editingMessageId != null) {
                        val msgRef = database.child("messages").child(conversationId).child(editingMessageId!!)
                        msgRef.child("content").setValue(newMessage.trim() + " (modifié)")
                        editingMessageId = null
                    } else {
                        val msg = mapOf(
                            "senderId" to senderId,
                            "receiverId" to receiverId,
                            "content" to newMessage.trim(),
                            "timestamp" to System.currentTimeMillis(),
                            "read" to false
                        )
                        val newRef = database.child("messages").child(conversationId).push()
                        newRef.setValue(msg)

                        // Conversation
                        database.child("conversations").child(senderId).child(receiverId).setValue(true)
                        database.child("conversations").child(receiverId).child(senderId).setValue(true)
                    }
                    newMessage = ""
                }
            }) {
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

data class MessageWithId(
    val message: Message,
    val id: String
)

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val read: Boolean? = null
)
