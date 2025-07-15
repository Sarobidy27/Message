package com.example.message.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

fun uriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
    val bytes = outputStream.toByteArray()
    return Base64.encodeToString(bytes, Base64.DEFAULT)
}

data class MessageWithId(val message: Message, val id: String)

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val read: Boolean? = null,
    val expiryTimestamp: Long? = null,
    val imageBase64: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(backStackEntry: NavBackStackEntry) {
    val context = LocalContext.current
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
    var imageBase64ToSend by remember { mutableStateOf<String?>(null) }
    var showZoomImage by remember { mutableStateOf(false) }
    var zoomedImageBase64 by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: android.net.Uri? ->
            if (uri != null) {
                val base64 = uriToBase64(context, uri)
                imageBase64ToSend = base64
            }
        }
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    LaunchedEffect(receiverId) {
        database.child("users").child(receiverId).child("Nom").get()
            .addOnSuccessListener {
                userName = it.getValue(String::class.java) ?: "Utilisateur"
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

    LaunchedEffect(messages) {
        while (true) {
            delay(3000)
            val nowTimestamp = System.currentTimeMillis()
            messages.forEach { msgWithId ->
                val expiry = msgWithId.message.expiryTimestamp
                if (expiry != null && nowTimestamp > expiry) {
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
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(userName)
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
                    DropdownMenu(expanded = ephemeralMenuExpanded, onDismissRequest = { ephemeralMenuExpanded = false }) {
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Messages list
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
                    val hasText = msg.content.isNotBlank()
                    val hasImage = msg.imageBase64 != null
                    val showBubble = hasText || !hasImage

                    val countdown = msg.expiryTimestamp?.let {
                        val remaining = (it - now) / 1000
                        if (remaining > 0) "$remaining s" else null
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { selectedMessageId = null },
                                onLongClick = { selectedMessageId = msgWithId.id }
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .then(if (showBubble) Modifier.background(
                                    if (isMe) Color(0xFFD1E7DD) else Color(0xFFE2E3E5),
                                    shape = MaterialTheme.shapes.large
                                ) else Modifier)
                                .padding(12.dp)
                                .widthIn(0.dp, 280.dp)
                        ) {
                            Column {
                                if (hasText) {
                                    Text(msg.content, fontSize = 17.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                msg.imageBase64?.let {
                                    val bytes = Base64.decode(it, Base64.DEFAULT)
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    bmp?.let { bitmap ->
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 250.dp)
                                                .clickable {
                                                    showZoomImage = true
                                                    zoomedImageBase64 = it
                                                }
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(time, fontSize = 12.sp, color = Color.Gray)
                                    countdown?.let {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("⏳ $it", fontSize = 12.sp, color = Color.Red)
                                    }
                                    if (isMe && msg.read == true) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color.Blue, modifier = Modifier.size(16.dp))
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
                                    imageBase64ToSend = msg.imageBase64 // preload image for edit
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

            Spacer(modifier = Modifier.height(8.dp))

            // Input and image preview row
            Column {
                if (imageBase64ToSend != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        val bytes = Base64.decode(imageBase64ToSend, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        bmp?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Image à envoyer",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clickable {
                                        showZoomImage = true
                                        zoomedImageBase64 = imageBase64ToSend
                                    }
                            )
                        }
                        IconButton(onClick = { imageBase64ToSend = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Supprimer l'image", tint = Color.Red)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Écrire...") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { launcher.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = "Ajouter une image")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (newMessage.isNotBlank() || imageBase64ToSend != null) {
                            if (editingMessageId != null) {
                                val msgRef = database.child("messages").child(conversationId).child(editingMessageId!!)
                                msgRef.child("content").setValue(newMessage.trim() + " (modifié)")
                                msgRef.child("imageBase64").setValue(imageBase64ToSend)
                                editingMessageId = null
                            } else {
                                val now = System.currentTimeMillis()
                                val expiry = if (expirationDurationSeconds > 0) now + expirationDurationSeconds * 1000 else null
                                val msg = mutableMapOf<String, Any>(
                                    "senderId" to senderId,
                                    "receiverId" to receiverId,
                                    "content" to newMessage.trim(),
                                    "timestamp" to now,
                                    "read" to false
                                )
                                expiry?.let { msg["expiryTimestamp"] = it }
                                imageBase64ToSend?.let { msg["imageBase64"] = it }
                                database.child("messages").child(conversationId).push().setValue(msg)
                                database.child("conversations").child(senderId).child(receiverId).setValue(true)
                                database.child("conversations").child(receiverId).child(senderId).setValue(true)
                            }
                            newMessage = ""
                            imageBase64ToSend = null
                        }
                    }) {
                        Text(if (editingMessageId != null) "Modifier" else "Envoyer")
                    }
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

    if (showZoomImage && zoomedImageBase64 != null) {
        val bytes = Base64.decode(zoomedImageBase64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.let {
            AlertDialog(
                onDismissRequest = { showZoomImage = false },
                confirmButton = {
                    TextButton(onClick = { showZoomImage = false }) {
                        Text("Fermer")
                    }
                },
                title = {},
                text = {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Image Zoomée",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp)
                    )
                }
            )
        }
    }
}
