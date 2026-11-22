package app.morphe.extension.shared.patches.toolkit.similarity;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import app.morphe.extension.shared.diagnostics.SafeLog;

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
            SafeLog.e("morphe: ImageSimilarityEngine", "calculateDifferenceHash error: " + t.getMessage(), t);
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
     * Quality comparison to determine which item in a duplicate cluster to keep as the "Original / Best".
     * Order of preference:
     * 1. Favorite items (user explicitly favorited).
     * 2. Higher resolution (width * height).
     * 3. Larger file size / quota bytes (less compressed).
     * 4. Earliest capture timestamp (original shot).
     */
    public static int compareQuality(MediaItem a, MediaItem b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        // 1. Favor favorite items
        if (a.isFavorite != b.isFavorite) {
            return a.isFavorite ? -1 : 1;
        }

        // 2. Favor higher resolution
        long resA = (long) a.width * a.height;
        long resB = (long) b.width * b.height;
        if (resA != resB) {
            return Long.compare(resB, resA);
        }

        // 3. Favor larger file size
        long sizeA = a.sizeBytes > 0 ? a.sizeBytes : a.quotaBytes;
        long sizeB = b.sizeBytes > 0 ? b.sizeBytes : b.quotaBytes;
        if (sizeA != sizeB) {
            return Long.compare(sizeB, sizeA);
        }

        // 4. Favor earlier capture date
        if (a.captureTimestamp > 0 && b.captureTimestamp > 0 && a.captureTimestamp != b.captureTimestamp) {
            return Long.compare(a.captureTimestamp, b.captureTimestamp);
        }

        return 0;
    }

    /**
     * Groups similar media items into duplicate clusters according to the given threshold.
     * Items in each cluster are sorted by quality, with the best item at index 0 (isDuplicate = false)
     * and redundant items at index 1..N (isDuplicate = true).
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

        int cId = 1;
        for (List<MediaItem> cluster : clusters) {
            cluster.sort((a, b) -> compareQuality(a, b));
            for (int i = 0; i < cluster.size(); i++) {
                MediaItem it = cluster.get(i);
                it.clusterId = cId;
                it.isDuplicate = (i > 0);
            }
            cId++;
        }

        return clusters;
    }
}
