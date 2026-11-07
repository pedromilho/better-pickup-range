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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
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
    private final PickupRanges.Rules originalRules;
    private boolean permissionsOpen;
    private boolean individual;
    private boolean adminsOnly;
    private int personalSteps;
    private int universalSteps;

    PickupRangePage(PlayerRef player, PickupRanges ranges, PickupPreview preview, float defaultRadius) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.ranges = ranges;
        this.preview = preview;
        this.defaultRadius = defaultRadius;
        String language = player.getLanguage();
        portuguese = language != null
            && "pt".equals(Locale.forLanguageTag(language.replace('_', '-')).getLanguage());
        originalRules = ranges.rules();
        individual = originalRules.individual();
        adminsOnly = originalRules.adminsOnly();
        personalSteps = ranges.get(player.getUuid());
        universalSteps = originalRules.universalSteps();
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder ui, UIEventBuilder events, Store<EntityStore> store) {
        ui.append("Pages/BetterPickupRange.ui");
        if (portuguese) {
            ui.set("#Heading.Text", "ALCANCE DE COLETA");
            ui.set("#Reset.Text", "Padrão");
            ui.set("#Cancel.Text", "Cancelar");
            ui.set("#Apply.Text", "Aplicar");
            ui.set("#PermissionsButton.Text", "Permissões dos jogadores");
            ui.set("#PermissionsHeading.Text", "PERMISSÕES DOS JOGADORES");
            ui.set("#Back.Text", "Voltar");
            ui.set("#ApplyPermissions.Text", "Aplicar");
            ui.set("#IndividualLabel.Text", "Cada jogador define seu alcance");
            ui.set("#AccessLabel.Text", "Acesso ao comando");
            ui.set("#AllPlayers.Text", "Todos os jogadores");
            ui.set("#AdminsOnly.Text", "Só administradores");
        }
        updateControls(ui, PickupRangePlugin.isAdmin(playerRef), true);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Range",
            EventData.of("Action", "slide").append("@Steps", "#Range.Value"), false);
        bind(events, "#Minus", "less");
        bind(events, "#Plus", "more");
        bind(events, "#Reset", "reset");
        bind(events, "#KeepPreview", "togglePreview");
        ui.set("#KeepPreview.Text", previewToggleLabel());
        bind(events, "#Cancel", "cancel");
        bind(events, "#Apply", "apply");
        bind(events, "#PermissionsButton", "permissions");
        bind(events, "#Back", "back");
        bind(events, "#ApplyPermissions", "apply");
        bindCheck(events, "#Individual", "individual");
        bind(events, "#AllPlayers", "allPlayers");
        bind(events, "#AdminsOnly", "adminsOnly");
        setCamera();
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data event) {
        boolean admin = PickupRangePlugin.isAdmin(playerRef);
        if (!admin && (ranges.rules().adminsOnly() || permissionsOpen)) {
            close();
            return;
        }
        if (ranges.rules() != originalRules) {
            permissionsChanged();
            return;
        }
        if (event.action == null) {
            sendUpdate();
            return;
        }
        switch (event.action) {
            case "permissions" -> {
                if (!admin) {
                    sendUpdate();
                    return;
                }
                permissionsOpen = true;
            }
            case "back" -> permissionsOpen = false;
            case "individual" -> {
                if (!admin || !permissionsOpen || event.checked == null) {
                    sendUpdate();
                    return;
                }
                individual = event.checked;
            }
            case "allPlayers", "adminsOnly" -> {
                if (!admin || !permissionsOpen) {
                    sendUpdate();
                    return;
                }
                adminsOnly = "adminsOnly".equals(event.action);
            }
            case "less" -> setSteps(displayedSteps() - 1, admin);
            case "more" -> setSteps(displayedSteps() + 1, admin);
            case "reset" -> setSteps(PickupRanges.DEFAULT, admin);
            case "slide" -> {
                if (event.steps == null) {
                    sendUpdate();
                    return;
                }
                setSteps(event.steps, admin);
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
                    boolean saved = admin
                        ? ranges.saveRules(originalRules, new PickupRanges.Rules(individual, adminsOnly, universalSteps),
                            playerRef.getUuid(), personalSteps)
                        : ranges.savePersonal(playerRef.getUuid(), personalSteps);
                    if (!saved) {
                        permissionsChanged();
                        return;
                    }
                    close();
                } catch (IOException e) {
                    String message = portuguese ? "Falha ao salvar. Tente novamente." : "Could not save. Try again.";
                    if (permissionsOpen) {
                        playerRef.sendMessage(Message.raw(message));
                        sendUpdate();
                    } else {
                        sendUpdate(new UICommandBuilder().set("#Hint.Text", message));
                    }
                }
                return;
            }
            default -> {
                sendUpdate();
                return;
            }
        }
        var ui = new UICommandBuilder();
        updateControls(ui, admin, !"slide".equals(event.action));
        sendUpdate(ui);
    }

    private void permissionsChanged() {
        playerRef.sendMessage(Message.raw(portuguese
            ? "As permissões mudaram. Abra /pickuprange novamente."
            : "Permissions changed. Reopen /pickuprange."));
        close();
    }

    private void setSteps(int selected, boolean admin) {
        if (permissionsOpen) {
            return;
        }
        int clamped = Math.clamp(selected, PickupRanges.DEFAULT, PickupRanges.MAX);
        if (individual) {
            personalSteps = clamped;
        } else if (admin) {
            universalSteps = clamped;
        }
    }

    private void updateControls(UICommandBuilder ui, boolean admin, boolean syncSlider) {
        boolean rangeEditable = admin || individual;
        int displayedSteps = displayedSteps();
        ui.set("#RangeScreen.Visible", !permissionsOpen);
        ui.set("#PermissionsScreen.Visible", permissionsOpen);
        ui.set("#PermissionsButton.Visible", admin);
        ui.set("#Apply.Visible", rangeEditable);
        ui.set("#Range.Visible", rangeEditable);
        ui.set("#Minus.Visible", rangeEditable);
        ui.set("#Plus.Visible", rangeEditable);
        ui.set("#Reset.Visible", rangeEditable);
        if (admin && permissionsOpen) {
            ui.set("#Individual.Value", individual);
            ui.set("#AllPlayers.Disabled", !adminsOnly);
            ui.set("#AdminsOnly.Disabled", adminsOnly);
            return;
        }
        if (syncSlider || !rangeEditable) {
            int height = 191 + (rangeEditable ? 32 : 0) + (admin ? 34 : 0);
            var screenAnchor = new Anchor();
            screenAnchor.setHeight(Value.of(height));
            screenAnchor.setBottom(Value.of(24));
            ui.setObject("#RangeScreen.Anchor", screenAnchor);
            var panelAnchor = new Anchor();
            panelAnchor.setWidth(Value.of(422));
            panelAnchor.setHeight(Value.of(height));
            ui.setObject("#RangePanel.Anchor", panelAnchor);
        }
        ui.set("#Value.Text", rangeLabel(displayedSteps));
        if (syncSlider || !rangeEditable) {
            ui.set("#Range.Value", displayedSteps);
        }
        ui.set("#Hint.Text", individual ? previewHint()
            : (portuguese ? "Alcance universal" : "Universal pickup range"));
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

    private String rangeLabel(int steps) {
        float radius = radiusFor(steps);
        String label = portuguese
            ? String.format(Locale.forLanguageTag("pt-BR"), "%.2f blocos", radius)
            : String.format(Locale.ENGLISH, "%.2f blocks", radius);
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
        return radiusFor(displayedSteps());
    }

    private float radiusFor(int steps) {
        return steps == PickupRanges.DEFAULT ? defaultRadius : PickupRanges.blocks(steps);
    }

    private int displayedSteps() {
        return individual ? personalSteps : universalSteps;
    }

    private static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action));
    }

    private static void bindCheck(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector,
            EventData.of("Action", action).append("@Checked", selector + ".Value"));
    }

    static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value,
                data -> data.action).add()
            .append(new KeyedCodec<>("@Steps", Codec.INTEGER), (data, value) -> data.steps = value,
                data -> data.steps).add()
            .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (data, value) -> data.checked = value,
                data -> data.checked).add()
            .build();

        private String action;
        private Integer steps;
        private Boolean checked;
    }
}
