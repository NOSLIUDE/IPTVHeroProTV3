package com.iptvhero.pro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iptvhero.pro.data.local.ProfileEntity
import com.iptvhero.pro.data.repository.IptvRepository

@Composable
fun ProfileManagerDialog(
    profiles: List<ProfileEntity> = emptyList(),
    activeProfileId: Long? = null,
    onDismiss: () -> Unit,
    onActivateProfile: (Long) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onImportContent: (String) -> Unit,
    onImportGithubHero: (String) -> Unit = {},
    onImportXtream: (String, String, String) -> Unit = { _, _, _ -> }
) {
    var mode by remember { mutableStateOf("LIST") }
    var rawTxtContent by remember { mutableStateOf("") }
    var githubUrl by remember { mutableStateOf(IptvRepository.HERO_LIST_URL) }
    var xcServer by remember { mutableStateOf("") }
    var xcUser by remember { mutableStateOf("") }
    var xcPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gerenciar Perfis e Listas") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (mode) {
                    "LIST" -> {
                        Button(
                            onClick = { onImportGithubHero(githubUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Baixar Hero Lista (GitHub)")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { mode = "TXT_M3U" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Colar TXT / M3U")
                            }
                            OutlinedButton(
                                onClick = { mode = "XTREAM" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Xtream Codes")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Perfis Salvos no Banco:", style = MaterialTheme.typography.titleSmall)

                        if (profiles.isEmpty()) {
                            Text("Nenhum perfil cadastrado.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(profiles, key = { it.id }) { profile ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (profile.id == activeProfileId)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${profile.name} (${profile.sourceType})",
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row {
                                                if (profile.id != activeProfileId) {
                                                    TextButton(onClick = { onActivateProfile(profile.id) }) {
                                                        Text("Ativar")
                                                    }
                                                }
                                                TextButton(onClick = { onDeleteProfile(profile.id) }) {
                                                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "TXT_M3U" -> {
                        OutlinedTextField(
                            value = githubUrl,
                            onValueChange = { githubUrl = it },
                            label = { Text("URL da Lista TXT / M3U (GitHub)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Ou cole o conteudo TXT / M3U / URL Xtream abaixo:", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = rawTxtContent,
                            onValueChange = { rawTxtContent = it },
                            label = { Text("Ex: #EXTM3U ou [Esportes]\nCanal 1,https://...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            maxLines = 10
                        )
                    }

                    "XTREAM" -> {
                        OutlinedTextField(
                            value = xcServer,
                            onValueChange = { xcServer = it },
                            label = { Text("Servidor (http://exemplo.com:8080)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = xcUser,
                            onValueChange = { xcUser = it },
                            label = { Text("Usuario") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = xcPass,
                            onValueChange = { xcPass = it },
                            label = { Text("Senha") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (mode) {
                "TXT_M3U" -> {
                    Button(onClick = {
                        if (rawTxtContent.isNotBlank()) {
                            onImportContent(rawTxtContent)
                            rawTxtContent = ""
                            mode = "LIST"
                        } else if (githubUrl.isNotBlank()) {
                            onImportGithubHero(githubUrl)
                            mode = "LIST"
                        }
                    }) {
                        Text("Processar e Salvar")
                    }
                }

                "XTREAM" -> {
                    Button(onClick = {
                        if (xcServer.isNotBlank() && xcUser.isNotBlank() && xcPass.isNotBlank()) {
                            onImportXtream(xcServer, xcUser, xcPass)
                            mode = "LIST"
                        }
                    }) {
                        Text("Conectar Xtream")
                    }
                }

                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar")
                    }
                }
            }
        },
        dismissButton = {
            if (mode != "LIST") {
                TextButton(onClick = { mode = "LIST" }) {
                    Text("Voltar")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}
