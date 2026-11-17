package xyz.wallpanel.app.modules;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LumaMotionDetectorTest {
    private byte[] frame(int width, int height, int luma) {
        byte[] frame = new byte[width * height];
        Arrays.fill(frame, (byte) (luma + 16));
        return frame;
    }

    @Test public void identicalFramesStayStill() {
        LumaMotionDetector detector = new LumaMotionDetector(0, 20);
        byte[] frame = frame(640, 480, 80);
        for (int i = 0; i < 20; i++) assertEquals(LumaMotionDetector.STILL, detector.detect(frame, 640, 480));
    }

    @Test public void reusingCameraBufferStillDetectsMovement() {
        LumaMotionDetector detector = new LumaMotionDetector(0, 20);
        byte[] frame = frame(640, 480, 50);
        assertEquals(LumaMotionDetector.STILL, detector.detect(frame, 640, 480));
        for (int y = 0; y < 48; y++) Arrays.fill(frame, y * 640, y * 640 + 64, (byte) 116);
        assertEquals(LumaMotionDetector.MOTION, detector.detect(frame, 640, 480));
        assertEquals(LumaMotionDetector.STILL, detector.detect(frame, 640, 480));
    }

    @Test public void thresholdIsStrictAndUsesBlockAverage() {
        LumaMotionDetector detector = new LumaMotionDetector(0, 20);
        assertEquals(LumaMotionDetector.STILL, detector.detect(frame(20, 20, 50), 20, 20));
        assertEquals(LumaMotionDetector.STILL, detector.detect(frame(20, 20, 70), 20, 20));
        assertEquals(LumaMotionDetector.MOTION, detector.detect(frame(20, 20, 91), 20, 20));
    }

    @Test public void darkFramesDoNotReplaceTheLastVisibleFrame() {
        LumaMotionDetector detector = new LumaMotionDetector(1000, 20);
        assertEquals(LumaMotionDetector.STILL, detector.detect(frame(20, 20, 50), 20, 20));
        assertEquals(LumaMotionDetector.TOO_DARK, detector.detect(frame(20, 20, 0), 20, 20));
        assertEquals(LumaMotionDetector.STILL, detector.detect(frame(20, 20, 50), 20, 20));
    }

    @Test public void oddDimensionsIncludeTheBottomRightPixel() {
        LumaMotionDetector detector = new LumaMotionDetector(0, 20);
        byte[] frame = frame(23, 27, 50);
        assertEquals(LumaMotionDetector.STILL, detector.detect(frame, 23, 27));
        frame[frame.length - 1] = (byte) 166;
        assertEquals(LumaMotionDetector.MOTION, detector.detect(frame, 23, 27));
    }

    @Test public void resolutionChangeAndSeparateInstancesStartCleanly() {
        LumaMotionDetector first = new LumaMotionDetector(0, 20);
        first.detect(frame(20, 20, 50), 20, 20);
        assertEquals(LumaMotionDetector.STILL, first.detect(frame(13, 11, 150), 13, 11));
        LumaMotionDetector second = new LumaMotionDetector(0, 20);
        assertEquals(LumaMotionDetector.STILL, second.detect(frame(13, 11, 50), 13, 11));
        assertEquals(LumaMotionDetector.MOTION, first.detect(frame(13, 11, 50), 13, 11));
    }

    @Test(expected = IllegalArgumentException.class)
    public void truncatedFramesAreRejected() {
        new LumaMotionDetector(0, 20).detect(new byte[10], 20, 20);
    }
}
