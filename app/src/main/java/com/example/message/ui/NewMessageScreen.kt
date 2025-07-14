package com.example.message.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth

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
                // Recherche utilisateur par nom (simplifié ici)
                db.child("users").get().addOnSuccessListener { snapshot ->
                    val recipientUid = snapshot.children.firstOrNull {
                        it.child("Nom").value == recipientName
                    }?.key

                    if (recipientUid != null) {
                        val messageData = mapOf(
                            "from" to senderUid,
                            "to" to recipientUid,
                            "message" to message
                        )
                        db.child("messages").push().setValue(messageData)
                        confirmation = "Message envoyé !"
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
