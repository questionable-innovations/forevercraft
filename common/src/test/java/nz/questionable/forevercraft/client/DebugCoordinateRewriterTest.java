package nz.questionable.forevercraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DebugCoordinateRewriterTest {
    // Spawn at true (8016, 70, -4096); offset is that chunk's corner.
    private static final int OX = 8016;
    private static final int OZ = -4096;

    @Test
    void xyzLineIsShifted() {
        assertEquals("XYZ: 3.500 / 70.00000 / -2.250",
                DebugCoordinateRewriter.rewriteLeftLine("XYZ: 8019.500 / 70.00000 / -4098.250", OX, OZ));
    }

    @Test
    void blockLineIsShiftedAndChunkRelativeStaysConsistent() {
        assertEquals("Block: 3 70 -3 [3 6 13]",
                DebugCoordinateRewriter.rewriteLeftLine("Block: 8019 70 -4099 [3 6 13]", OX, OZ));
    }

    @Test
    void chunkLineHidesTrueRegion() {
        // true chunk 501,-257 is region r.15.-9; shown chunk 0,-1 is region r.0.-1
        assertEquals("Chunk: 0 4 -1 [0 31 in r.0.-1.mca]",
                DebugCoordinateRewriter.rewriteLeftLine("Chunk: 501 4 -257 [21 31 in r.15.-9.mca]", OX, OZ));
    }

    @Test
    void unrelatedLinesAreLeftAlone() {
        assertNull(DebugCoordinateRewriter.rewriteLeftLine("Facing: north (Towards negative Z) (0.0 / 0.0)", OX, OZ));
        assertNull(DebugCoordinateRewriter.rewriteLeftLine("minecraft:overworld FC: 0", OX, OZ));
    }

    @Test
    void targetedBlockKeepsFormattingPrefix() {
        List<String> right = new ArrayList<>(List.of("§nTargeted Block: 8020, 64, -4090", "minecraft:stone",
                "§nTargeted Fluid: 8000, 62, -4100"));
        DebugCoordinateRewriter.rewriteRight(right, OX, OZ);
        assertEquals(List.of("§nTargeted Block: 4, 64, 6", "minecraft:stone", "§nTargeted Fluid: -16, 62, -4"), right);
    }

    @Test
    void distantHorizonsLodPositionIsDropped() {
        List<String> right = new ArrayList<>(List.of("Distant Horizons: 3.3.3", "LOD Pos: 6 7*-4",
                "Rendering API: OpenGL"));
        DebugCoordinateRewriter.rewriteRight(right, OX, OZ);
        assertEquals(List.of("Distant Horizons: 3.3.3", "Rendering API: OpenGL"), right);
    }

    @Test
    void zeroOffsetRoundTrips() {
        String line = "XYZ: -12.301 / 64.00000 / 5.000";
        assertEquals(line, DebugCoordinateRewriter.rewriteLeftLine(line, 0, 0));
    }
}
