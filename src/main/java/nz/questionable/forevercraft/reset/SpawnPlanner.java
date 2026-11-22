package nz.questionable.forevercraft.reset;

import java.util.List;
import java.util.Random;

/** Picks where the next era's spawn goes, in true block coordinates. Pure logic for unit tests. */
public final class SpawnPlanner {
    public record Point(int x, int z) {
        double distanceTo(int ox, int oz) {
            double dx = x - ox;
            double dz = z - oz;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    private static final int ATTEMPTS = 512;

    private SpawnPlanner() {}

    /**
     * A point {@code minDistance..maxDistance} from {@code last} and at least {@code minDistance} from every
     * previous spawn, preferring points within {@code preferredRadius} of the origin.
     */
    public static Point pickCenter(List<Point> previous, Point last, int minDistance, int maxDistance,
                                   int preferredRadius, int borderLimit, Random rng) {
        int max = Math.max(minDistance, maxDistance);
        Point outsidePreferred = null;
        // Widen the ring if earlier eras have crowded out the requested one.
        for (int widen = 0; widen < 16; widen++) {
            int lo = minDistance + widen * minDistance;
            int hi = max + widen * minDistance;
            for (int i = 0; i < ATTEMPTS; i++) {
                double angle = rng.nextDouble() * Math.PI * 2;
                double dist = lo + rng.nextDouble() * (hi - lo);
                int x = last.x() + (int) Math.round(Math.cos(angle) * dist);
                int z = last.z() + (int) Math.round(Math.sin(angle) * dist);
                if (Math.abs(x) > borderLimit || Math.abs(z) > borderLimit) continue;
                if (!farFromAll(previous, x, z, minDistance)) continue;
                Point p = new Point(x, z);
                if (preferredRadius > 0 && p.distanceTo(0, 0) > preferredRadius) {
                    if (outsidePreferred == null) outsidePreferred = p;
                    continue;
                }
                return p;
            }
            if (outsidePreferred != null) return outsidePreferred;
        }
        throw new IllegalStateException("No room for a new spawn inside the world border");
    }

    static boolean farFromAll(List<Point> previous, int x, int z, int minDistance) {
        for (Point p : previous) {
            if (p.distanceTo(x, z) < minDistance) return false;
        }
        return true;
    }
}
