package com.zacknetic.zoomintegration.zoom.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Helps retry failed operations with exponential backoff.
 *
 * When calling external APIs, sometimes things fail temporarily - maybe the network
 * hiccupped, or the service is momentarily overloaded. Instead of immediately giving up,
 * this utility will retry a few times with increasing delays between attempts. This gives
 * the external service time to recover without hammering it with requests.
 */
public class RetryUtil {

    private static final Logger log = LoggerFactory.getLogger(RetryUtil.class);

    /**
     * Configuration for retry behavior - how many times to retry, how long to wait, etc.
     */
    public static class RetryConfig {
        private final int maxRetries;
        private final long baseDelayMs;
        private final long maxDelayMs;
        private final double multiplier;

        public RetryConfig(int maxRetries, long baseDelayMs, long maxDelayMs, double multiplier) {
            this.maxRetries = maxRetries;
            this.baseDelayMs = baseDelayMs;
            this.maxDelayMs = maxDelayMs;
            this.multiplier = multiplier;
        }

        public static RetryConfig defaultConfig() {
            return new RetryConfig(3, 1000, 10000, 2.0);
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public long getBaseDelayMs() {
            return baseDelayMs;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }

        public double getMultiplier() {
            return multiplier;
        }
    }

    /**
     * Runs an operation and retries it if it fails, waiting longer between each attempt.
     *
     * For example, if you're calling an API and it returns a 503 error, we'll wait 1 second
     * and try again. If it fails again, we'll wait 2 seconds, then 4 seconds, and so on
     * until we either succeed or run out of retries.
     *
     * @param operation The thing you're trying to do (like making an API call)
     * @param config Settings like max retries and delays
     * @param operationName A friendly name for logging (e.g., "Zoom OAuth token fetch")
     * @return Whatever your operation returns if it succeeds
     * @throws IOException If all retry attempts fail
     */
    public static <T> T withRetry(
            Supplier<T> operation,
            RetryConfig config,
            String operationName) throws IOException {

        IOException lastException = null;
        int attempt = 0;

        while (attempt <= config.getMaxRetries()) {
            try {
                if (attempt > 0) {
                    log.info("Retry attempt {}/{} for operation: {}",
                            attempt, config.getMaxRetries(), operationName);
                }

                return operation.get();

            } catch (Exception e) {
                lastException = (e instanceof IOException)
                    ? (IOException) e
                    : new IOException("Operation failed: " + operationName, e);

                attempt++;

                if (attempt > config.getMaxRetries()) {
                    log.error("All retry attempts exhausted for operation: {}", operationName);
                    break;
                }

                long delay = calculateDelay(attempt, config);
                log.warn("Operation '{}' failed (attempt {}/{}), retrying in {}ms. Error: {}",
                        operationName, attempt, config.getMaxRetries(), delay, e.getMessage());

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted for operation: " + operationName, ie);
                }
            }
        }

        throw lastException != null
            ? lastException
            : new IOException("Operation failed after retries: " + operationName);
    }

    /**
     * Figures out how long to wait before the next retry attempt.
     *
     * Uses exponential backoff: first retry waits baseDelay, second waits baseDelay * 2,
     * third waits baseDelay * 4, etc. But we cap it at maxDelay so we don't wait forever.
     *
     * @param attempt Which retry attempt this is (1, 2, 3...)
     * @param config The retry settings
     * @return How many milliseconds to wait
     */
    private static long calculateDelay(int attempt, RetryConfig config) {
        long delay = (long) (config.getBaseDelayMs() * Math.pow(config.getMultiplier(), attempt - 1));
        return Math.min(delay, config.getMaxDelayMs());
    }

    /**
     * Checks if we should retry after getting this HTTP error code.
     *
     * Some errors are worth retrying (like 503 Service Unavailable or 429 Rate Limited),
     * because they're temporary. Others aren't worth retrying (like 401 Unauthorized),
     * because trying again won't help - you need to fix the auth first.
     *
     * @param statusCode The HTTP status code we got back
     * @return true if we should try again, false if it's pointless
     */
    public static boolean isRetryable(int statusCode) {
        // 5xx server errors are retryable
        if (statusCode >= 500 && statusCode < 600) {
            return true;
        }

        // 429 Too Many Requests is retryable
        if (statusCode == 429) {
            return true;
        }

        // 408 Request Timeout is retryable
        if (statusCode == 408) {
            return true;
        }

        // All other errors are not retryable
        return false;
    }
}
