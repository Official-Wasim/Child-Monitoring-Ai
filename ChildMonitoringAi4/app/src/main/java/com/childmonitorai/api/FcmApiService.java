package com.childmonitorai.api;
    

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface FcmApiService {
    @Headers({
        "Authorization: key=YOUR_SERVER_KEY", // Replace with your FCM server key
        "Content-Type: application/json"
    })
    @POST("fcm/send")
    Call<Map<String, Object>> sendMessage(@Body Map<String, Object> message);
}

}
