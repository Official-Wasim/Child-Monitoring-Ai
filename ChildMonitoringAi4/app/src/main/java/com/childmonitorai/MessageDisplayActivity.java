package com.childmonitorai;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MessageDisplayActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_display);

        String message = getIntent().getStringExtra("message");
        String title = getIntent().getStringExtra("title");

        TextView titleView = findViewById(R.id.message_title);
        TextView messageView = findViewById(R.id.message_content);
        Button closeButton = findViewById(R.id.close_button);

        titleView.setText(title != null ? title : "Message");
        messageView.setText(message);

        closeButton.setOnClickListener(v -> finish());
    }
}
