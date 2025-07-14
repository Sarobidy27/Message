package com.example.message.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import androidx.navigation.NavBackStackEntry

@Composable
fun MessageScreen(backStackEntry: NavBackStackEntry) {
    val receiverId = backStackEntry.arguments?.getString("receiverId") ?: return
    val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance().reference
    val conversationId = listOf(senderId, receiverId).sorted().joinToString("_")
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var newMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val ref = database.child("messages").child(conversationId)
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Message>()
                for (child in snapshot.children) {
                    val msg = child.getValue(Message::class.java)
                    msg?.let { list.add(it) }
                }
                messages = list
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            messages.forEach { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = if (msg.senderId == senderId) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (msg.senderId == senderId) Color(0xFFD1E7DD) else Color(0xFFE2E3E5),
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(8.dp)
                    ) {
                        Text(msg.content)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = newMessage,
                onValueChange = { newMessage = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Écrire...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val msg = Message(senderId, receiverId, newMessage, System.currentTimeMillis())
                val newRef = database.child("messages").child(conversationId).push()
                newRef.setValue(msg)
                // Créer la conversation si elle n'existe pas encore
                database.child("conversations").child(senderId).child(receiverId).setValue(true)
                database.child("conversations").child(receiverId).child(senderId).setValue(true)
                newMessage = ""
            }) {
                Text("Envoyer")
            }
        }
    }
}

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = 0L
)
