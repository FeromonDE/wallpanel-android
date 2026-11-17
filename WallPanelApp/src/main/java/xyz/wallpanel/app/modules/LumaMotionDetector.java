package xyz.wallpanel.app.modules;

/**
 * Compares average luminance in small blocks directly from the NV21 Y plane.
 * Keeps only block statistics, never copies or retains a camera frame.
 */
public final class LumaMotionDetector {
    public static final int TOO_DARK = -1;
    public static final int STILL = 0;
    public static final int MOTION = 1;

    private final int minimumLuma;
    private final int leniency;
    private int width;
    private int height;
    private int boxWidth;
    private int boxHeight;
    private int columns;
    private int rows;
    private int[] previous = new int[0];
    private int[] current = new int[0];
    private boolean hasPrevious;

    public LumaMotionDetector(int minimumLuma, int leniency) {
        this.minimumLuma = minimumLuma;
        this.leniency = leniency;
    }

    public int detect(byte[] nv21, int frameWidth, int frameHeight) {
        if (nv21 == null || frameWidth <= 0 || frameHeight <= 0
                || (long) frameWidth * frameHeight > nv21.length) {
            throw new IllegalArgumentException("Invalid luminance frame");
        }
        if (width != frameWidth || height != frameHeight) {
            width = frameWidth;
            height = frameHeight;
            boxWidth = Math.max(1, width / 10);
            boxHeight = Math.max(1, height / 10);
            columns = (width + boxWidth - 1) / boxWidth;
            rows = (height + boxHeight - 1) / boxHeight;
            previous = new int[columns * rows];
            current = new int[columns * rows];
            hasPrevious = false;
        }
        long totalLuma = 0;
        for (int blockY = 0; blockY < rows; blockY++) {
            final int top = blockY * boxHeight;
            final int bottom = Math.min(top + boxHeight, height);
            for (int blockX = 0; blockX < columns; blockX++) {
                final int left = blockX * boxWidth;
                final int right = Math.min(left + boxWidth, width);
                int sum = 0;
                for (int y = top; y < bottom; y++) {
                    int index = y * width + left;
                    for (int x = left; x < right; x++, index++) {
                        sum += Math.max(0, (nv21[index] & 0xff) - 16);
                    }
                }
                current[blockY * columns + blockX] = sum;
                totalLuma += sum;
            }
        }
        // Preserve the existing setting's total-luminance scale.
        if (totalLuma < minimumLuma) return TOO_DARK;

        boolean changed = false;
        for (int y = 0; y < rows; y++) {
            final int actualHeight = Math.min(boxHeight, height - y * boxHeight);
            for (int x = 0; x < columns; x++) {
                final int actualWidth = Math.min(boxWidth, width - x * boxWidth);
                final int block = y * columns + x;
                current[block] /= actualWidth * actualHeight;
                if (hasPrevious && Math.abs(current[block] - previous[block]) > leniency) {
                    changed = true;
                }
            }
        }
        final int[] reusable = previous;
        previous = current;
        current = reusable;
        hasPrevious = true;
        return changed ? MOTION : STILL;
    }
}
