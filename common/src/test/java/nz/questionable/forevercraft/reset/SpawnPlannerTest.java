package nz.questionable.forevercraft.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import nz.questionable.forevercraft.reset.SpawnPlanner.Point;

class SpawnPlannerTest {
    private static final int BORDER = 29_999_984 - 1024;

    @Test
    void eachNewSpawnIsInRangeOfLastAndFarFromAll() {
        Random rng = new Random(42);
        List<Point> spawns = new ArrayList<>(List.of(new Point(48, -32)));
        for (int era = 1; era <= 40; era++) {
            Point last = spawns.get(spawns.size() - 1);
            Point next = SpawnPlanner.pickCenter(spawns, last, 6000, 10000, 20000, BORDER, rng);
            for (Point p : spawns) {
                assertTrue(p.distanceTo(next.x(), next.z()) >= 6000, "era " + era + " too close to " + p);
            }
            spawns.add(next);
        }
    }

    @Test
    void earlyErasStayInsidePreferredRadius() {
        Random rng = new Random(7);
        List<Point> spawns = new ArrayList<>(List.of(new Point(0, 0)));
        for (int era = 1; era <= 5; era++) {
            Point last = spawns.get(spawns.size() - 1);
            Point next = SpawnPlanner.pickCenter(spawns, last, 6000, 10000, 20000, BORDER, rng);
            double fromLast = last.distanceTo(next.x(), next.z());
            assertTrue(fromLast >= 6000 && fromLast <= 10000, "era " + era + " distance " + fromLast);
            assertTrue(next.distanceTo(0, 0) <= 20000, "era " + era + " outside preferred radius");
            spawns.add(next);
        }
    }

    @Test
    void respectsBorder() {
        Random rng = new Random(1);
        Point next = SpawnPlanner.pickCenter(List.of(new Point(0, 0)), new Point(0, 0), 6000, 10000, 0, 12000, rng);
        assertTrue(Math.abs(next.x()) <= 12000 && Math.abs(next.z()) <= 12000);
    }

    @Test
    void ringCoversEveryCellOnce() {
        for (int r = 0; r <= 5; r++) {
            Set<Long> seen = new HashSet<>();
            for (int[] o : SpawnLocator.ring(r)) {
                assertEquals(r, Math.max(Math.abs(o[0]), Math.abs(o[1])));
                assertTrue(seen.add(((long) o[0] << 32) ^ (o[1] & 0xffffffffL)), "duplicate in ring " + r);
            }
            assertEquals(r == 0 ? 1 : 8 * r, seen.size());
        }
    }
}
