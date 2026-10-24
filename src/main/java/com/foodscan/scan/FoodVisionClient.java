package com.foodscan.scan;

/** Identifies foods and estimates portions in a meal photo. */
public interface FoodVisionClient {

    record Result(MealAnalysis analysis, String model, long inputTokens, long outputTokens) {
    }

    /** Thrown when the model can't produce a usable analysis. The message is safe to show users. */
    class VisionException extends RuntimeException {
        public VisionException(String message) {
            super(message);
        }
    }

    Result analyze(byte[] image, String contentType);
}
