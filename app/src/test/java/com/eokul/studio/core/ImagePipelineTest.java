package com.eokul.studio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImagePipelineTest {
    private static int[] solid(int w, int h, int rgb) {
        int[] p = new int[w * h];
        java.util.Arrays.fill(p, 0xFF000000 | rgb);
        return p;
    }

    @Test
    public void finishKeepsSizeAndColor() {
        int[] src = solid(1200, 1600, 0x808080);
        int[] out = Finish.render(src, 1200, 1600, new double[]{150, 150, 1050, 1370}, 133, 171, new Finish.Opts());
        assertEquals(133 * 171, out.length);
        int c = out[out.length / 2] & 255;
        assertTrue("Düz renk bozulmamalı: " + c, Math.abs(c - 128) <= 2);
    }

    @Test
    public void jpegHasHeaderAndMinimumSize() {
        int[] px = solid(133, 171, 0x6090C0);
        byte[] jpg = JpegEncoder.encode(px, 133, 171, 100, 300, null, 20 * 1024 + 200);
        assertEquals(0xFF, jpg[0] & 255);
        assertEquals(0xD8, jpg[1] & 255);
        assertTrue("En az 20 KB: " + jpg.length, jpg.length >= 20 * 1024);
    }

    @Test
    public void qualityDetectsBlurAndDarkness() {
        int w = 600, h = 800;
        int[] sharp = new int[w * h];
        int[] dark = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = (((x / 6) + (y / 6)) % 2 == 0) ? 200 : 60;
                sharp[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
                int d = v / 6;
                dark[y * w + x] = 0xFF000000 | (d << 16) | (d << 8) | d;
            }
        }
        double[] box = {50, 50, 550, 750};
        assertTrue(Quality.analyze(dark, w, h, box).dark());
        assertTrue(!Quality.analyze(sharp, w, h, box).dark());
    }
}
