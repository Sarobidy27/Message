package com.example.message.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth
import java.util.*

@Composable
fun NewMessageScreen(navController: NavController) {
    var recipientName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }

    val db = FirebaseDatabase.getInstance().reference
    val senderUid = FirebaseAuth.getInstance().currentUser?.uid

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Envoyer un message", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = recipientName,
            onValueChange = { recipientName = it },
            label = { Text("Nom du destinataire") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (recipientName.isNotBlank() && message.isNotBlank() && senderUid != null) {
                // Recherche de l'utilisateur par nom
                db.child("users").get().addOnSuccessListener { snapshot ->
                    val recipientEntry = snapshot.children.firstOrNull {
                        it.child("Nom").value == recipientName
                    }

                    val recipientUid = recipientEntry?.key

                    if (recipientUid != null) {
                        val conversationId = listOf(senderUid, recipientUid).sorted().joinToString("_")

                        val timestamp = System.currentTimeMillis()

                        val messageData = mapOf(
                            "senderId" to senderUid,
                            "receiverId" to recipientUid,
                            "message" to message,
                            "timestamp" to timestamp,
                            "read" to false
                        )


                        db.child("messages").child(conversationId).push().setValue(messageData)

                        db.child("conversations").child(senderUid).child(recipientUid).setValue(true)
                        db.child("conversations").child(recipientUid).child(senderUid).setValue(true)

                        confirmation = "Message envoyé !"

                        navController.popBackStack()
                    } else {
                        confirmation = "Utilisateur non trouvé."
                    }
                }
            } else {
                confirmation = "Champs obligatoires."
            }
        }) {
            Text("Envoyer")
        }

        confirmation?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}
