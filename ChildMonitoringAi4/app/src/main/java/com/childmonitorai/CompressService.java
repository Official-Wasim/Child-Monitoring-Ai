package com.childmonitorai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;

public class CompressService {
    private static final int JPEG_QUALITY = 80;
    private static final int MAX_WIDTH = 1280;
    private static final int MAX_HEIGHT = 720;

    public byte[] compressImage(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return null;
        }

        // Decode image dimensions
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);

        // Calculate scale factor
        int scale = calculateScaleFactor(options.outWidth, options.outHeight);

        // Decode with scale factor
        options.inJustDecodeBounds = false;
        options.inSampleSize = scale;
        
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);
        if (bitmap == null) {
            return null;
        }

        // Compress to JPEG
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
        bitmap.recycle();

        return outputStream.toByteArray();
    }

    private int calculateScaleFactor(int width, int height) {
        int scale = 1;
        while (width / scale > MAX_WIDTH || height / scale > MAX_HEIGHT) {
            scale *= 2;
        }
        return scale;
    }
}
