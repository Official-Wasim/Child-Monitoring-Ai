package com.childmonitorai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class LoginActivity extends AppCompatActivity {
    private EditText emailField, passwordField, nameField, confirmPasswordField;
    private Button actionButton;
    private TextView loginChip, signupChip, forgotPasswordText;
    private LinearLayout loginForm;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private DatabaseReference database;
    private boolean isLoginMode = true;
    private boolean isForgotPasswordMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initializeFirebase();
        initializeViews();
        setupClickListeners();
        checkCurrentUser();
    }

    private void initializeFirebase() {
        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance().getReference("users");
    }

    private void initializeViews() {
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        nameField = findViewById(R.id.nameField);
        confirmPasswordField = findViewById(R.id.confirmPasswordField);
        actionButton = findViewById(R.id.actionButton);
        loginChip = findViewById(R.id.loginChip);
        signupChip = findViewById(R.id.signupChip);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        loginForm = findViewById(R.id.loginForm);
        progressBar = findViewById(R.id.progressBar);

        // Initial UI state
        loginChip.setSelected(true);
        signupChip.setSelected(false);
        actionButton.setText("Login");
        updateUIForMode();
    }

    private void setupClickListeners() {
        loginChip.setOnClickListener(v -> {
            isForgotPasswordMode = false;
            switchMode(true);
        });
        signupChip.setOnClickListener(v -> {
            isForgotPasswordMode = false;
            switchMode(false);
        });
        actionButton.setOnClickListener(v -> handleFormSubmission());
        forgotPasswordText.setOnClickListener(v -> {
            isForgotPasswordMode = true;
            updateUIForMode();
        });
    }

    private void updateUIForMode() {
        if (isForgotPasswordMode) {
            nameField.setVisibility(View.GONE);
            passwordField.setVisibility(View.GONE);
            confirmPasswordField.setVisibility(View.GONE);
            actionButton.setText("Reset Password");
            loginChip.setSelected(false);
            signupChip.setSelected(false);
            forgotPasswordText.setVisibility(View.GONE);
        } else {
            passwordField.setVisibility(View.VISIBLE);
            nameField.setVisibility(isLoginMode ? View.GONE : View.VISIBLE);
            confirmPasswordField.setVisibility(isLoginMode ? View.GONE : View.VISIBLE);
            actionButton.setText(isLoginMode ? "Login" : "Sign Up");
            loginChip.setSelected(isLoginMode);
            signupChip.setSelected(!isLoginMode);
            forgotPasswordText.setVisibility(isLoginMode ? View.VISIBLE : View.GONE);
        }
    }

    private void switchMode(boolean loginMode) {
        isLoginMode = loginMode;
        isForgotPasswordMode = false;
        updateUIForMode();
    }

    private void checkCurrentUser() {
        if (auth.getCurrentUser() != null) {
            navigateToMainActivity();
        }
    }

    private void handleFormSubmission() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();
        String name = nameField.getText().toString().trim();
        String confirmPassword = confirmPasswordField.getText().toString().trim();

        if (isForgotPasswordMode) {
            if (!email.isEmpty()) {
                handleForgotPassword();
            } else {
                Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (isLoginMode) {
            if (validateLoginInput(email, password)) {
                showProgressBar(true);
                handleLogin(email, password);
            }
        } else {
            if (validateSignUpInput(email, password, confirmPassword, name)) {
                showProgressBar(true);
                handleSignUp(email, password, name);
            }
        }
    }

    private boolean validateLoginInput(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
        }

        private boolean validateSignUpInput(String email, String password, String confirmPassword, String name) {
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (name.matches(".*\\d.*")) {
            Toast.makeText(this, "Name should not contain numbers.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
        }

        private void handleLogin(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    showProgressBar(false);
                    if (task.isSuccessful()) {
                        String userId = auth.getCurrentUser().getUid();
                        String phoneModel = android.os.Build.MODEL;
                        createOrUpdateUserNode(userId, phoneModel, email);
                        navigateToMainActivity();
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "Login failed. " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleSignUp(String email, String password, String name) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = auth.getCurrentUser().getUid();
                        String phoneModel = android.os.Build.MODEL;
                        createOrUpdateUserNode(userId, phoneModel, email, name);
                        navigateToMainActivity();
                    } else {
                        showProgressBar(false);
                        Toast.makeText(LoginActivity.this,
                                "Error: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleForgotPassword() {
        String email = emailField.getText().toString().trim();

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressBar(true);
        
        // Directly send reset email without checking
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showProgressBar(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this,
                                "If an account exists with this email, a password reset link will be sent.",
                                Toast.LENGTH_LONG).show();
                        isForgotPasswordMode = false;
                        switchMode(true);
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "Error: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showProgressBar(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loginForm.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void createOrUpdateUserNode(String userId, String phoneModel, String email) {
        DatabaseReference userPhoneDataRef = database.child(userId)
                .child("phones")
                .child(phoneModel);

        userPhoneDataRef.child("email").setValue(email)
                .addOnSuccessListener(aVoid -> {});
    }

    private void createOrUpdateUserNode(String userId, String phoneModel, String email, String name) {
        DatabaseReference userPhoneDataRef = database.child(userId)
                .child("phones")
                .child(phoneModel);

        // Set email at phone level
        userPhoneDataRef.child("email").setValue(email);

        // Set name in user-details
        userPhoneDataRef.child("user-details")
                .child("name")
                .setValue(name)
                .addOnSuccessListener(aVoid -> {
                    showProgressBar(false);
                });
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}