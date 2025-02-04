package com.childmonitorai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {
    private EditText emailField, passwordField;
    private Button loginButton, signUpButton;
    private TextView forgotPasswordText;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private DatabaseReference database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance().getReference("users");

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        loginButton = findViewById(R.id.loginButton);
        signUpButton = findViewById(R.id.signUpButton);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        progressBar = findViewById(R.id.progressBar);

        // Skip login if already logged in
        if (auth.getCurrentUser() != null) {
            navigateToMainActivity();
        }

        loginButton.setOnClickListener(view -> handleLogin());
        signUpButton.setOnClickListener(view -> handleSignUp());
        forgotPasswordText.setOnClickListener(view -> handleForgotPassword());
    }

    private void handleLogin() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        if (!email.isEmpty() && !password.isEmpty()) {
            showProgressBar(true);
            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        showProgressBar(false);
                        if (task.isSuccessful()) {
                            String userId = auth.getCurrentUser().getUid(); 
                            String phoneModel = android.os.Build.MODEL;
                            createOrUpdateUserNode(userId, phoneModel, email);
                            navigateToMainActivity();
                        } else {
                            Toast.makeText(LoginActivity.this, "Login failed. " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSignUp() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        if (!email.isEmpty() && !password.isEmpty()) {
            showProgressBar(true);
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(createTask -> {
                        showProgressBar(false);
                        if (createTask.isSuccessful()) {
                            String userId = auth.getCurrentUser().getUid();  
                            String phoneModel = android.os.Build.MODEL;
                            createOrUpdateUserNode(userId, phoneModel, email);
                            navigateToMainActivity();
                        } else {
                            Toast.makeText(LoginActivity.this, "Error: " + createTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleForgotPassword() {
        String email = emailField.getText().toString().trim();

        if (!email.isEmpty()) {
            showProgressBar(true);
            auth.fetchSignInMethodsForEmail(email)
                    .addOnCompleteListener(task -> {
                        showProgressBar(false);
                        if (task.isSuccessful() && !task.getResult().getSignInMethods().isEmpty()) {
                            auth.sendPasswordResetEmail(email)
                                    .addOnCompleteListener(resetTask -> {
                                        if (resetTask.isSuccessful()) {
                                            Toast.makeText(LoginActivity.this, "Password reset email sent.", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(LoginActivity.this, "Error: " + resetTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        } else {
                            Toast.makeText(LoginActivity.this, "Email not registered.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgressBar(boolean show) {
        progressBar.setVisibility(show ? ProgressBar.VISIBLE : ProgressBar.GONE);
    }

    private void createOrUpdateUserNode(String userId, String phoneModel, String email) {
        DatabaseReference userPhoneDataRef = database.child(userId)
                .child("phones")
                .child(phoneModel);

        userPhoneDataRef.child("email").setValue(email)
                .addOnSuccessListener(aVoid -> {}); 
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
