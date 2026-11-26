package nz.questionable.forevercraft.client;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shifts the coordinate lines of the F3 screen by the era offset. Pure string logic (no Minecraft types)
 * so it can be unit tested; formats match DebugScreenOverlay in 1.20.1 and 1.21.1.
 */
public final class DebugCoordinateRewriter {
    private static final String NUM = "(-?\\d+(?:\\.\\d+)?)";
    private static final String INT = "(-?\\d+)";

    private static final Pattern XYZ = Pattern.compile("^XYZ: " + NUM + " / " + NUM + " / " + NUM + "$");
    private static final Pattern BLOCK = Pattern.compile("^Block: " + INT + " " + INT + " " + INT + " \\[\\d+ \\d+ \\d+]$");
    private static final Pattern CHUNK = Pattern.compile(
            "^Chunk: " + INT + " " + INT + " " + INT + " \\[\\d+ \\d+ in r\\.-?\\d+\\.-?\\d+\\.mca]$");
    // Right-hand side lines carry a formatting prefix (underline), so match anywhere.
    private static final String DH_LOD_POS = "LOD Pos: ";
    private static final Pattern TARGETED = Pattern.compile("(Targeted (?:Block|Fluid): )" + INT + ", " + INT + ", " + INT);

    private DebugCoordinateRewriter() {}

    public static void rewriteLeft(List<String> lines, int offsetX, int offsetZ) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) continue;
            String rewritten = rewriteLeftLine(line, offsetX, offsetZ);
            if (rewritten != null) lines.set(i, rewritten);
        }
    }

    public static void rewriteRight(List<String> lines, int offsetX, int offsetZ) {
        // Distant Horizons' "LOD Pos" is a section position derived from true chunk coordinates.
        lines.removeIf(line -> line != null && line.startsWith(DH_LOD_POS));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) continue;
            Matcher m = TARGETED.matcher(line);
            if (m.find()) {
                int x = Integer.parseInt(m.group(2)) - offsetX;
                int z = Integer.parseInt(m.group(4)) - offsetZ;
                String replacement = m.group(1) + x + ", " + m.group(3) + ", " + z;
                lines.set(i, line.substring(0, m.start()) + replacement + line.substring(m.end()));
            }
        }
    }

    /** @return the shifted line, or null if the line carries no coordinates. */
    static String rewriteLeftLine(String line, int offsetX, int offsetZ) {
        Matcher m = XYZ.matcher(line);
        if (m.matches()) {
            double x = Double.parseDouble(m.group(1)) - offsetX;
            double z = Double.parseDouble(m.group(3)) - offsetZ;
            return String.format(Locale.ROOT, "XYZ: %.3f / %s / %.3f", x, m.group(2), z);
        }
        m = BLOCK.matcher(line);
        if (m.matches()) {
            int x = Integer.parseInt(m.group(1)) - offsetX;
            int y = Integer.parseInt(m.group(2));
            int z = Integer.parseInt(m.group(3)) - offsetZ;
            return String.format(Locale.ROOT, "Block: %d %d %d [%d %d %d]", x, y, z, x & 15, y & 15, z & 15);
        }
        m = CHUNK.matcher(line);
        if (m.matches()) {
            int cx = Integer.parseInt(m.group(1)) - (offsetX >> 4);
            int cy = Integer.parseInt(m.group(2));
            int cz = Integer.parseInt(m.group(3)) - (offsetZ >> 4);
            return String.format(Locale.ROOT, "Chunk: %d %d %d [%d %d in r.%d.%d.mca]",
                    cx, cy, cz, cx & 31, cz & 31, cx >> 5, cz >> 5);
        }
        return null;
    }
}
