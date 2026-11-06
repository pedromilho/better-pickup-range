package br.pedro.pickuprange;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PickupItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerItemEntityPickupSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.Set;
import org.joml.Vector3d;

final class ExtendedPickupSystem extends DelayedEntitySystem<EntityStore> {
    private final PickupRanges ranges;
    private final Query<EntityStore> players = Query.and(Player.getComponentType(), PlayerRef.getComponentType(),
        TransformComponent.getComponentType(), Query.not(DeathComponent.getComponentType()),
        Query.not(Spectating.getComponentType()));
    private final Query<EntityStore> drops = Query.and(ItemComponent.getComponentType(),
        TransformComponent.getComponentType(), Query.not(Interactable.getComponentType()),
        Query.not(PreventPickup.getComponentType()), Query.not(PickupItemComponent.getComponentType()),
        Query.not(PropComponent.getComponentType()));

    ExtendedPickupSystem(PickupRanges ranges) {
        super(0.10f);
        this.ranges = ranges;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return players;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, PlayerItemEntityPickupSystem.class));
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        var player = chunk.getComponent(index, Player.getComponentType());
        var identity = chunk.getComponent(index, PlayerRef.getComponentType());
        var position = chunk.getComponent(index, TransformComponent.getComponentType()).getPosition();
        if (player.getPageManager().getCustomPage() instanceof PickupRangePage preview) {
            preview.draw(position);
        }
        int steps = ranges.get(identity.getUuid());
        if (steps == PickupRanges.DEFAULT) {
            return;
        }

        float radius = PickupRanges.blocks(steps);
        var nearby = SpatialResource.<EntityStore>getThreadLocalReferenceList();
        var spatial = store.getResource(EntityModule.get().getItemSpatialResourceType()).getSpatialStructure();
        spatial.collect(position, radius, nearby);
        var world = store.getExternalData().getWorld();
        var playerEntity = chunk.getReferenceTo(index);
        for (var ref : nearby) {
            if (!ref.isValid() || !drops.test(store.getArchetype(ref))) {
                continue;
            }
            var drop = store.getComponent(ref, ItemComponent.getComponentType());
            var stack = drop.getItemStack();
            if (!drop.canPickUp() || drop.isRemovedByPlayerPickup() || ItemStack.isEmpty(stack)
                || stack.getItem().getInteractions().containsKey(InteractionType.Pickup)) {
                continue;
            }

            var origin = store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
            double distanceSquared = position.distanceSquared(origin);
            float nativeRadius = drop.getPickupRadius(store);
            if (nativeRadius <= 0 || distanceSquared <= nativeRadius * nativeRadius
                || distanceSquared > radius * radius || blocked(world, position, origin)) {
                continue;
            }

            var remainder = Player.giveItem(stack, playerEntity, buffer).getRemainder();
            int left = ItemStack.isEmpty(remainder) ? 0 : remainder.getQuantity();
            if (left == stack.getQuantity()) {
                continue;
            }
            if (left == 0) {
                drop.setRemovedByPlayerPickup(true);
                buffer.removeEntity(ref, RemoveReason.REMOVE);
            } else {
                drop.setItemStack(remainder);
            }
            var collected = stack.withQuantity(stack.getQuantity() - left);
            var effect = ItemComponent.generatePickedUpItem(collected, origin, buffer, playerEntity);
            if (effect != null) {
                buffer.addEntity(effect, AddReason.SPAWN);
            }
            Player.notifyPickupItem(playerEntity, collected, origin, buffer);
        }
    }

    private static boolean blocked(World world, Vector3d from, Vector3d to) {
        var start = new Vector3d(from).add(0, 0.25, 0);
        var direction = new Vector3d(to).add(0, 0.25, 0).sub(start);
        double length = direction.length();
        direction.div(length);
        return TargetUtil.getTargetBlock(world, (blockId, fluidId) -> {
            var block = BlockType.getAssetMap().getAsset(blockId);
            return block != null && block.getMaterial() == BlockMaterial.Solid;
        }, start.x, start.y, start.z, direction.x, direction.y, direction.z, length) != null;
    }
}
