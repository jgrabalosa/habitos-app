package com.joaquim.habitosapp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Component
public class FirebaseConfig {

    @Value("${firebase.credentials.base64:}")
    private String firebaseCredentialsBase64;

    @PostConstruct
    public void initialize() {
        if (firebaseCredentialsBase64 == null || firebaseCredentialsBase64.isBlank()) {
            System.out.println("⚠️ FIREBASE_CREDENTIALS_BASE64 no configurada. Firebase no se inicializará.");
            return;
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(firebaseCredentialsBase64);
            ByteArrayInputStream credentialsStream = new ByteArrayInputStream(decodedBytes);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("✅ Firebase inicializado correctamente.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error al inicializar Firebase: " + e.getMessage());
        }
    }
}