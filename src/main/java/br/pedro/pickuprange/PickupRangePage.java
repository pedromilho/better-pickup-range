package br.pedro.pickuprange;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.world.CancelParticleSystems;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3d;
import org.joml.Vector3dc;

final class PickupRangePage extends InteractiveCustomUIPage<PickupRangePage.Data> {
    private static final String PREVIEW_PARTICLE = "BetterPickupRange_Preview";
    private static final float PREVIEW_LIFETIME_SECONDS = 60;
    private static final long PREVIEW_REFRESH_NANOS = TimeUnit.SECONDS.toNanos(55);
    private final PickupRanges ranges;
    private final float defaultRadius;
    private int steps;
    private Vector3d previewCenter;
    private int previewSteps;
    private long previewStarted;

    PickupRangePage(PlayerRef player, PickupRanges ranges, float defaultRadius) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.ranges = ranges;
        this.defaultRadius = defaultRadius;
        steps = ranges.get(player.getUuid());
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder ui, UIEventBuilder events, Store<EntityStore> store) {
        ui.append("Pages/BetterPickupRange.ui");
        ui.set("#Value.Text", rangeLabel());
        ui.set("#Range.Value", steps);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Range",
            EventData.of("Action", "slide").append("@Steps", "#Range.Value"), false);
        bind(events, "#Minus", "less");
        bind(events, "#Plus", "more");
        bind(events, "#Reset", "reset");
        bind(events, "#Cancel", "cancel");
        bind(events, "#Apply", "apply");
        setCamera();
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data event) {
        if (event.action == null) {
            sendUpdate();
            return;
        }
        switch (event.action) {
            case "less" -> steps = Math.max(PickupRanges.DEFAULT, steps - 1);
            case "more" -> steps = Math.min(PickupRanges.MAX, steps + 1);
            case "reset" -> steps = PickupRanges.DEFAULT;
            case "slide" -> {
                if (event.steps == null) {
                    sendUpdate();
                    return;
                }
                steps = Math.clamp(event.steps, PickupRanges.DEFAULT, PickupRanges.MAX);
            }
            case "cancel" -> {
                close();
                return;
            }
            case "apply" -> {
                try {
                    ranges.save(playerRef.getUuid(), steps);
                    close();
                } catch (IOException e) {
                    sendUpdate(new UICommandBuilder().set("#Hint.Text", "Falha ao salvar. Tente novamente."));
                }
                return;
            }
            default -> {
                sendUpdate();
                return;
            }
        }
        var ui = new UICommandBuilder();
        ui.set("#Value.Text", rangeLabel());
        if (!"slide".equals(event.action)) {
            ui.set("#Range.Value", steps);
        }
        ui.set("#Hint.Text", "O círculo mostra o alcance selecionado");
        sendUpdate(ui);
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        clearPreview();
        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
    }

    void draw(Vector3dc position) {
        long now = System.nanoTime();
        if (previewCenter != null && previewSteps == steps
            && previewCenter.distanceSquared(position) < 0.05 * 0.05
            && now - previewStarted < PREVIEW_REFRESH_NANOS) {
            return;
        }
        clearPreview();
        float radius = selectedRadius();
        if (!Float.isFinite(radius) || radius <= 0) {
            return;
        }
        playerRef.getPacketHandler().writeNoCache(new SpawnParticleSystem(PREVIEW_PARTICLE,
            new Position(position.x(), position.y() + 0.12, position.z()), new Direction(0, 0, 0),
            radius * 2, null, PREVIEW_LIFETIME_SECONDS));
        previewCenter = new Vector3d(position);
        previewSteps = steps;
        previewStarted = now;
    }

    private void clearPreview() {
        if (previewCenter != null) {
            playerRef.getPacketHandler().writeNoCache(
                new CancelParticleSystems(null, null, new String[] { PREVIEW_PARTICLE }, true));
            previewCenter = null;
        }
    }

    private void setCamera() {
        var camera = new ServerCameraSettings();
        camera.positionLerpSpeed = 0.25f;
        camera.rotationLerpSpeed = 0.25f;
        camera.distance = 18f;
        camera.displayCursor = true;
        camera.isFirstPerson = false;
        camera.hideHeldItem = true;
        camera.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
        camera.rotationType = RotationType.Custom;
        camera.rotation = new Direction(0.55f, -1.02f, 0);
        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, camera));
    }

    private String rangeLabel() {
        String label = String.format(Locale.forLanguageTag("pt-BR"), "%.2f blocos", selectedRadius());
        return steps == PickupRanges.DEFAULT ? label + " · Padrão do jogo" : label;
    }

    private float selectedRadius() {
        return steps == PickupRanges.DEFAULT ? defaultRadius : PickupRanges.blocks(steps);
    }

    private static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action));
    }

    static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value,
                data -> data.action).add()
            .append(new KeyedCodec<>("@Steps", Codec.INTEGER), (data, value) -> data.steps = value,
                data -> data.steps).add()
            .build();

        private String action;
        private Integer steps;
    }
}
