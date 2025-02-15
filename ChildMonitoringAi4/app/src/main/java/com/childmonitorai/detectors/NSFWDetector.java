package com.childmonitorai.detectors;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.provider.MediaStore;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import java.io.InputStream;
import java.io.IOException;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.Map;


public class NSFWDetector {
    private static final String TAG = "NSFWDetector";
    private static final String MODEL_PATH = "nsfw_mobilenetv2.tflite";
    private static final int IMAGE_SIZE = 224;
    private static final int FLOAT_TYPE_SIZE = 4;
    private static final int PIXEL_SIZE = 3;
    private static final int BATCH_SIZE = 1;
    private static final int OUTPUT_CLASSES = 5;
    private final Context context;
    private Interpreter tflite;
    private final ImageProcessor imageProcessor;
    private ByteBuffer inputBuffer;

    public NSFWDetector(Context context) {
        this.context = context;
        this.imageProcessor = createImageProcessor(); // Initialize in constructor
        
        try {
            Interpreter.Options options = new Interpreter.Options();
            MappedByteBuffer model = FileUtil.loadMappedFile(context, MODEL_PATH);
            tflite = new Interpreter(model, options);
            
            // Initialize input buffer with correct size and order
            inputBuffer = ByteBuffer.allocateDirect(
                BATCH_SIZE * IMAGE_SIZE * IMAGE_SIZE * PIXEL_SIZE * FLOAT_TYPE_SIZE);
            inputBuffer.order(ByteOrder.nativeOrder());
            
            Log.d(TAG, "NSFW model loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error loading NSFW model: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ImageProcessor createImageProcessor() {
        return new ImageProcessor.Builder()
            .add(new ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(new NormalizeOp(127.5f, 127.5f))  // Normalize to [-1,1]
            .build();
    }

    public Map<String, Float> analyzeImage(String imagePath) {
        Map<String, Float> result = new HashMap<>();

        try {
            Uri imageUri = getImageContentUri(imagePath);
            if (imageUri == null) {
                Log.e(TAG, "Could not get content URI for image: " + imagePath);
                return result;
            }

            // Load bitmap with proper rotation
            Bitmap originalBitmap = loadBitmapWithRotation(imageUri);
            if (originalBitmap == null) {
                Log.e(TAG, "Could not load image: " + imagePath);
                return result;
            }

            try {
                // Create TensorImage with correct data type
                TensorImage tensorImage = new TensorImage(tflite.getInputTensor(0).dataType());
                tensorImage.load(originalBitmap);

                // Process the image
                tensorImage = imageProcessor.process(tensorImage);

                // Prepare input buffer
                inputBuffer.rewind();
                ByteBuffer imageBuffer = tensorImage.getBuffer();
                inputBuffer.put(imageBuffer);

                // Run inference and process results
                float[][] outputArray = new float[1][OUTPUT_CLASSES];
                Object[] inputs = {inputBuffer};
                Map<Integer, Object> outputs = new HashMap<>();
                outputs.put(0, outputArray);

                tflite.runForMultipleInputsOutputs(inputs, outputs);

                // Process results
                processResults(result, outputArray[0]);

            } finally {
                // Clean up bitmap
                if (originalBitmap != null && !originalBitmap.isRecycled()) {
                    originalBitmap.recycle();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error analyzing image: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    private void processResults(Map<String, Float> result, float[] scores) {
        // Store individual class scores
        result.put("drawings", scores[0]);
        result.put("hentai", scores[1]);
        result.put("neutral", scores[2]);
        result.put("porn", scores[3]);
        result.put("sexy", scores[4]);

        // Calculate aggregate scores
        float sfwScore = scores[2];  // neutral class
        float nsfwScore = scores[0] + scores[1] + scores[3] + scores[4];

        result.put("sfw_score", sfwScore);
        result.put("nsfw_score", nsfwScore);

        Log.d(TAG, String.format(
                "Image analysis complete - SFW: %.2f, NSFW: %.2f\n" +
                        "Details: drawings=%.2f, hentai=%.2f, neutral=%.2f, porn=%.2f, sexy=%.2f",
                sfwScore, nsfwScore, scores[0], scores[1], scores[2], scores[3], scores[4]));
    }

    private Bitmap loadBitmapWithRotation(Uri imageUri) throws IOException {
        InputStream input = null;
        InputStream exifInput = null;
        Bitmap bitmap = null;
        
        try {
            input = context.getContentResolver().openInputStream(imageUri);
            bitmap = BitmapFactory.decodeStream(input);
            
            exifInput = context.getContentResolver().openInputStream(imageUri);
            ExifInterface exif = new ExifInterface(exifInput);
            
            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotationDegrees = 0;

            switch (rotation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotationDegrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotationDegrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotationDegrees = 270;
                    break;
            }

            return rotationDegrees == 0 ? bitmap : rotateBitmap(bitmap, rotationDegrees);
            
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream", e);
                }
            }
            if (exifInput != null) {
                try {
                    exifInput.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing exif input stream", e);
                }
            }
            if (bitmap != null && bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);

        try {
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return rotatedBitmap;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory while rotating bitmap");
            return bitmap;
        }
    }

    private Uri getImageContentUri(String imagePath) {
        ContentResolver resolver = context.getContentResolver();
        String[] projection = {MediaStore.Images.Media._ID};
        String selection = MediaStore.Images.Media.DATA + "=?";
        String[] selectionArgs = new String[]{imagePath};

        try (Cursor cursor = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null)) {

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            } else {
                Log.e(TAG, "Image not found in MediaStore: " + imagePath);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting content URI: " + e.getMessage());
            return null;
        }
    }

    public void close() {
        try {
            if (tflite != null) {
                tflite.close();
                tflite = null;
            }
            if (inputBuffer != null) {
                inputBuffer.clear();
                inputBuffer = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing resources", e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}
