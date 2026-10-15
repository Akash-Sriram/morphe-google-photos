package app.morphe.extension.shared.patches.toolkit.similarity;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.patches.toolkit.StorageScanner.MediaItem;

/**
 * Visual perceptual hashing (dHash) and clustering engine for finding duplicate
 * or near-identical images in Google Photos.
 */
public final class ImageSimilarityEngine {

    private ImageSimilarityEngine() {}

    /**
     * Difference Hash (dHash) — 9×8 grid → 64 horizontal gradient bits.
     * Robust against brightness, contrast, and color-filter variations.
     */
    public static long calculateDifferenceHash(Bitmap bitmap, int hashSize) {
        if (bitmap == null || bitmap.isRecycled()) return 0;
        try {
            Bitmap swBitmap = bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                swBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            }
            if (swBitmap == null || swBitmap.isRecycled()) return 0;

            int cols = 9;
            int rows = 8;
            Bitmap scaled = Bitmap.createScaledBitmap(swBitmap, cols, rows, true);
            int[] pixels = new int[cols * rows];
            scaled.getPixels(pixels, 0, cols, 0, 0, cols, rows);
            if (scaled != swBitmap && scaled != bitmap) scaled.recycle();
            if (swBitmap != bitmap) swBitmap.recycle();

            long hash = 0;
            int bit = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols - 1; c++) {
                    int left  = pixels[r * cols + c];
                    int right = pixels[r * cols + c + 1];
                    int gL = (Color.red(left)  * 299 + Color.green(left)  * 587 + Color.blue(left)  * 114) / 1000;
                    int gR = (Color.red(right) * 299 + Color.green(right) * 587 + Color.blue(right) * 114) / 1000;
                    if (gL < gR) {
                        hash |= (1L << bit);
                    }
                    bit++;
                }
            }
            return hash;
        } catch (Throwable t) {
            Log.e("morphe: ImageSimilarityEngine", "calculateDifferenceHash error: " + t.getMessage(), t);
            return 0;
        }
    }

    /**
     * Normalized Hamming distance between two 64-bit dHashes.
     * Returns 1.0 for identical hashes, 0.0 for completely opposite hashes.
     */
    public static float calculateSimilarity(long hashA, long hashB, int hashSize) {
        int totalBits = 64;
        int distance = Long.bitCount(hashA ^ hashB);
        return 1.0f - ((float) distance / totalBits);
    }

    /**
     * Groups similar media items into duplicate clusters according to the given threshold.
     */
    public static List<List<MediaItem>> clusterSimilarItems(List<MediaItem> items, float threshold, int hashSize) {
        List<List<MediaItem>> clusters = new ArrayList<>();
        if (items == null || items.isEmpty() || threshold <= 0) return clusters;

        for (MediaItem item : items) {
            if (item.perceptualHash == 0) continue;
            float bestSim = 0f;
            int bestClusterIdx = -1;

            for (int ci = 0; ci < clusters.size(); ci++) {
                List<MediaItem> cluster = clusters.get(ci);
                float maxSim = 0f;
                for (MediaItem member : cluster) {
                    float sim = calculateSimilarity(item.perceptualHash, member.perceptualHash, hashSize);
                    if (sim > maxSim) maxSim = sim;
                }
                if (maxSim >= threshold && maxSim > bestSim) {
                    bestSim = maxSim;
                    bestClusterIdx = ci;
                }
            }

            if (bestClusterIdx >= 0) {
                clusters.get(bestClusterIdx).add(item);
            } else {
                List<MediaItem> newCluster = new ArrayList<>();
                newCluster.add(item);
                clusters.add(newCluster);
            }
        }

        clusters.removeIf(cluster -> cluster.size() <= 1);
        return clusters;
    }
}
