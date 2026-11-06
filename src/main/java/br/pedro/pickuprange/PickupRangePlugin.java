package br.pedro.pickuprange;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class PickupRangePlugin extends JavaPlugin {
    public PickupRangePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        var ranges = new PickupRanges(getDataDirectory().resolve("ranges.properties"));
        getEntityStoreRegistry().registerSystem(new ExtendedPickupSystem(ranges));
        getCommandRegistry().registerCommand(new AbstractPlayerCommand("pickuprange", "Ajusta o alcance de coleta") {
            {
                requireNoPermission();
            }

            @Override
            protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                   PlayerRef player, World world) {
                var pages = store.getComponent(ref, Player.getComponentType()).getPageManager();
                if (!(pages.getCustomPage() instanceof PickupRangePage)) {
                    float defaultRadius = world.getGameplayConfig().getItemEntityConfig().getPickupRadius();
                    pages.openCustomPage(ref, store, new PickupRangePage(player, ranges, defaultRadius));
                }
            }
        });
    }
}
