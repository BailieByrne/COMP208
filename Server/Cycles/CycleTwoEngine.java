public class CycleTwoEngine implements CycleEngine {
    private final long durationMs;
    private long startedAtMs;
    private boolean running;

    // duration based placeholder cycle
    public CycleTwoEngine(long durationMs) {
        this.durationMs = durationMs;
    }

    @Override
    public String getName() {
        return "CYCLE2";
    }

    @Override
    public void start() {
        running = true;
        // mark cycle2 start clock
        startedAtMs = System.currentTimeMillis();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isComplete() {
        if (!running) {
            return false;
        }
        //Use better time than system to ensure all players booted at same time.
        return (System.currentTimeMillis() - startedAtMs) >= durationMs;
    }

    //Placeholder to last X seconds, we cna change this to send actual data if we want, but for now just a placeholder to show the new cycle is running
    public String placeholderPacket() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        long remaining = Math.max(0L, durationMs - elapsed);
        return String.format("{\"TYPE\":\"CYCLE2\",\"MESSAGE\":\"TILED_WORLD_PLACEHOLDER\",\"REMAINING_MS\":%d}", remaining);
    }
}
