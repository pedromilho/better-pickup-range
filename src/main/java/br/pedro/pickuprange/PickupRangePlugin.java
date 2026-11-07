package br.pedro.pickuprange;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.ConcurrentHashMap;

public final class PickupRangePlugin extends JavaPlugin {
    static boolean isAdmin(PlayerRef player) {
        return PermissionsModule.get().getGroupsForUser(player.getUuid()).contains("hytale:Admin")
            || player.hasPermission("pickuprange.admin");
    }

    public PickupRangePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        var ranges = new PickupRanges(getDataDirectory().resolve("ranges.properties"));
        var previews = new ConcurrentHashMap<PlayerRef, PickupPreview>();
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> previews.remove(event.getPlayerRef()));
        getEntityStoreRegistry().registerSystem(new ExtendedPickupSystem(ranges, previews));
        getCommandRegistry().registerCommand(new AbstractPlayerCommand("pickuprange", "Adjust your pickup range") {
            {
                requireNoPermission();
            }

            @Override
            protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                   PlayerRef player, World world) {
                if (ranges.rules().adminsOnly() && !isAdmin(player)) {
                    context.sendMessage(Message.raw("Only administrators can use /pickuprange."));
                    return;
                }
                var pages = store.getComponent(ref, Player.getComponentType()).getPageManager();
                if (!(pages.getCustomPage() instanceof PickupRangePage)) {
                    float defaultRadius = world.getGameplayConfig().getItemEntityConfig().getPickupRadius();
                    var preview = previews.computeIfAbsent(player, PickupPreview::new);
                    pages.openCustomPage(ref, store, new PickupRangePage(player, ranges, preview, defaultRadius));
                }
            }
        });
    }
}
