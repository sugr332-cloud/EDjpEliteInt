package elite.intel.bio.ccore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Reads an entire stream to completion on its own thread and hands back the accumulated text on
 * {@link #join()}.
 * <p>
 * Needed because stdout and stderr must be drained concurrently with writing stdin and waiting for
 * exit: reading either sequentially risks a deadlock if the child process fills that pipe's OS buffer
 * before this side gets around to reading it. {@link #join()} only returns once the underlying stream
 * hits EOF, which the caller must arrange (normal exit, or {@code Process.destroyForcibly()}) before
 * calling it - otherwise this blocks exactly as long as the process the timeout was meant to bound.
 */
final class StreamCollector {

    private final Thread thread;
    private final StringBuilder text = new StringBuilder();

    private StreamCollector(InputStream in, String threadName) {
        this.thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    text.append(buffer, 0, read);
                }
            } catch (IOException e) {
                // The stream closing early (e.g. destroyForcibly()) is expected on the timeout path,
                // not a failure worth surfacing - the caller already knows the process was abandoned.
            }
        }, threadName);
        thread.start();
    }

    static StreamCollector start(InputStream in, String threadName) {
        return new StreamCollector(in, threadName);
    }

    /** Blocks until the stream is fully drained (the process closed it) and returns everything read. */
    String join() {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return text.toString();
    }
}
