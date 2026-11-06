package br.pedro.pickuprange;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.Locale;

final class PickupRangePage extends InteractiveCustomUIPage<PickupRangePage.Data> {
    private final PickupRanges ranges;
    private final PickupPreview preview;
    private final float defaultRadius;
    private final boolean portuguese;
    private int steps;

    PickupRangePage(PlayerRef player, PickupRanges ranges, PickupPreview preview, float defaultRadius) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.ranges = ranges;
        this.preview = preview;
        this.defaultRadius = defaultRadius;
        String language = player.getLanguage();
        portuguese = language != null
            && "pt".equals(Locale.forLanguageTag(language.replace('_', '-')).getLanguage());
        steps = ranges.get(player.getUuid());
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder ui, UIEventBuilder events, Store<EntityStore> store) {
        ui.append("Pages/BetterPickupRange.ui");
        if (portuguese) {
            ui.set("#Heading.Text", "ALCANCE DE COLETA");
            ui.set("#Reset.Text", "Padrão");
            ui.set("#Cancel.Text", "Cancelar");
            ui.set("#Apply.Text", "Aplicar");
        }
        ui.set("#Hint.Text", previewHint());
        ui.set("#Value.Text", rangeLabel());
        ui.set("#Range.Value", steps);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Range",
            EventData.of("Action", "slide").append("@Steps", "#Range.Value"), false);
        bind(events, "#Minus", "less");
        bind(events, "#Plus", "more");
        bind(events, "#Reset", "reset");
        bind(events, "#KeepPreview", "togglePreview");
        ui.set("#KeepPreview.Text", previewToggleLabel());
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
            case "togglePreview" -> {
                preview.keepVisible = !preview.keepVisible;
                sendUpdate(new UICommandBuilder().set("#KeepPreview.Text", previewToggleLabel()));
                return;
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
                    String message = portuguese ? "Falha ao salvar. Tente novamente." : "Could not save. Try again.";
                    sendUpdate(new UICommandBuilder().set("#Hint.Text", message));
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
        ui.set("#Hint.Text", previewHint());
        sendUpdate(ui);
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!preview.keepVisible) {
            preview.clearPreview();
        }
        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
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
        String label = portuguese
            ? String.format(Locale.forLanguageTag("pt-BR"), "%.2f blocos", selectedRadius())
            : String.format(Locale.ENGLISH, "%.2f blocks", selectedRadius());
        if (steps == PickupRanges.DEFAULT) {
            label += portuguese ? " · Padrão do jogo" : " · Default";
        }
        return label;
    }

    private String previewHint() {
        return portuguese ? "O círculo mostra o alcance selecionado" : "The circle shows the selected range";
    }

    private String previewToggleLabel() {
        if (portuguese) {
            return preview.keepVisible ? "Prévia fora do painel: ligada" : "Prévia fora do painel: desligada";
        }
        return preview.keepVisible ? "Preview outside panel: on" : "Preview outside panel: off";
    }

    float selectedRadius() {
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
