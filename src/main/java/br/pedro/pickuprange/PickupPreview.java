package br.pedro.pickuprange;

import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.concurrent.TimeUnit;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class PickupPreview {
    private static final Vector3fc PREVIEW_COLOR = new Vector3f(0.44f, 0.96f, 0.88f);
    private static final float PREVIEW_LIFETIME_SECONDS = 10;
    private static final long PREVIEW_REFRESH_NANOS = TimeUnit.SECONDS.toNanos(9);

    private final PlayerRef playerRef;
    boolean keepVisible = true;
    private Vector3d previewCenter;
    private float previewRadius;
    private long previewStarted;

    PickupPreview(PlayerRef playerRef) {
        this.playerRef = playerRef;
    }

    void draw(Vector3dc position, float radius) {
        long now = System.nanoTime();
        if (previewCenter != null && previewRadius == radius
            && previewCenter.distanceSquared(position) < 0.05 * 0.05
            && now - previewStarted < PREVIEW_REFRESH_NANOS) {
            return;
        }
        clearPreview();
        if (!Float.isFinite(radius) || radius <= 0) {
            return;
        }
        var transform = new Matrix4d().translation(position.x(), position.y() + 0.12, position.z());
        // Match the native disc packet: outer radius, segment count, inner radius.
        float[] shape = new float[16];
        shape[0] = radius;
        shape[1] = 64;
        shape[2] = Math.max(0, radius - 0.06f);
        playerRef.getPacketHandler().writeNoCache(new DisplayDebug(DebugShape.Disc,
            Matrix4dUtil.asFloatData(transform), PREVIEW_COLOR, PREVIEW_LIFETIME_SECONDS,
            (byte) DebugUtils.FLAG_NO_WIREFRAME, shape, 0.9f));
        previewCenter = new Vector3d(position);
        previewRadius = radius;
        previewStarted = now;
    }

    void clearPreview() {
        if (previewCenter != null) {
            playerRef.getPacketHandler().writeNoCache(new ClearDebugShapes());
            previewCenter = null;
        }
    }
}
