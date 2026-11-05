package com.shouyun.theartofstockings.client;

import java.util.HashMap;
import java.util.Map;
import com.shouyun.theartofstockings.StockingsActions;
import com.shouyun.theartofstockings.StockingsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class ClientAnimations {
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static ClientLevel level;
    public record State(boolean equipping, net.minecraft.world.item.ItemStack stack, long start,com.shouyun.theartofstockings.WearSlot slot) {
        public float progress(LivingEntity entity, float partial) {
            return Mth.clamp((entity.level().getGameTime() - start + partial) / StockingsActions.DURATION, 0, 1);
        }
        public float seconds(LivingEntity entity, float partial) {
            return progress(entity, partial) * (StockingsActions.DURATION / 20F);
        }
        public float coverAmount(LivingEntity entity,float partial) {
            float p=progress(entity,partial);
            return slot==com.shouyun.theartofstockings.WearSlot.HEAD?HeadDressing.sample(equipping,p).coverage():equipping?p:1-p;
        }
        public com.shouyun.theartofstockings.AnimationClip clip() {
            var data = StockingsAssets.current;
            return data == null ? null : equipping ? data.putOn() : data.takeOff();
        }
        public float legAmount(LivingEntity entity, float partial, boolean left) {
            var clip = clip();
            return clip == null ? (equipping ? 0 : 1) : Mth.clamp(
                    clip.sample(left ? "left_pull" : "right_pull", "scale", seconds(entity, partial)).y(), 0, 1);
        }
    }

    private ClientAnimations() {}
    public static void clean() {
        var current = Minecraft.getInstance().level;
        if (level != current) { STATES.clear(); level = current; }
        if (current != null) STATES.values().removeIf(s -> current.getGameTime() - s.start > StockingsActions.DURATION + 5);
    }
    public static void receive(StockingsNetwork.Animation packet) {
        clean();
        if (level == null) return;
        if (!packet.active()) STATES.remove(packet.entityId());
        else STATES.put(packet.entityId(), new State(packet.equipping(), packet.stack().copy(), level.getGameTime() - packet.elapsed(),packet.slot()));
    }
    public static State get(LivingEntity entity) {
        clean();
        State s = STATES.get(entity.getId());
        return s != null && entity.level().getGameTime() - s.start <= StockingsActions.DURATION ? s : null;
    }
    public static void pose(PlayerModel<?> model, LivingEntity entity, float age) {
        State state = get(entity);
        if (state != null && state.slot()==com.shouyun.theartofstockings.WearSlot.LEGS || StockingsActions.wearing(entity)) {
            // The nylon sits against the base skin, without the baggy outer trouser shell.
            model.rightPants.visible = false;
            model.leftPants.visible = false;
        }
        if(entity instanceof net.minecraft.world.entity.player.Player player) {
            var body=com.shouyun.theartofstockings.StockingsWear.visual(player);
            boolean full=com.shouyun.theartofstockings.StockingsItem.style(body)==com.shouyun.theartofstockings.StockingsItem.Style.BODYSTOCKING;
            boolean fullAction=state!=null && state.slot()==com.shouyun.theartofstockings.WearSlot.LEGS
                    && com.shouyun.theartofstockings.StockingsItem.style(state.stack())==com.shouyun.theartofstockings.StockingsItem.Style.BODYSTOCKING;
            if(full || fullAction)model.jacket.visible=false;
            if(full || fullAction || state!=null && state.slot()==com.shouyun.theartofstockings.WearSlot.HANDS
                    || !com.shouyun.theartofstockings.StockingsWear.get(player,com.shouyun.theartofstockings.WearSlot.HANDS).isEmpty()) {
                model.rightSleeve.visible=false;model.leftSleeve.visible=false;
            }
            if(full || fullAction || state!=null && state.slot()==com.shouyun.theartofstockings.WearSlot.HEAD
                    || !com.shouyun.theartofstockings.StockingsWear.get(player,com.shouyun.theartofstockings.WearSlot.HEAD).isEmpty())model.hat.visible=false;
        }
        if(state!=null && state.slot()!=com.shouyun.theartofstockings.WearSlot.LEGS) {
            float p=state.progress(entity,age-entity.tickCount);
            float lift=Mth.sin(p*(float)Math.PI);
            float angle=-1.2F;
            if(state.slot()==com.shouyun.theartofstockings.WearSlot.HEAD) {
                var frame=HeadDressing.sample(state.equipping(),p);lift=frame.weight();angle=frame.armAngle();
                model.rightArm.y-=lift*1.2F;model.leftArm.y-=lift*1.2F;
                model.rightArm.zRot=Mth.lerp(lift,model.rightArm.zRot,-.18F);
                model.leftArm.zRot=Mth.lerp(lift,model.leftArm.zRot,.18F);
            }
            model.rightArm.xRot=Mth.lerp(lift,model.rightArm.xRot,angle);
            model.leftArm.xRot=Mth.lerp(lift,model.leftArm.xRot,angle);
            model.rightSleeve.copyFrom(model.rightArm);model.leftSleeve.copyFrom(model.leftArm);
            return;
        }
        if (state == null || state.clip() == null) return;
        float time = state.seconds(entity, age - entity.tickCount);
        float p = state.progress(entity, age - entity.tickCount);
        float weight = Mth.clamp(Math.min(p * 12, (1-p)*12),0,1);
        weight = weight*weight*(3-2*weight);
        float bodyY = model.body.y, bodyZ = model.body.z;
        float headY = model.head.y, headZ = model.head.z;
        float rightY = model.rightArm.y, rightZ = model.rightArm.z;
        float leftY = model.leftArm.y, leftZ = model.leftArm.z;
        apply(model.body, "body", state, time, weight);
        apply(model.head, "head", state, time, weight);
        apply(model.rightArm, "right_arm", state, time, weight);
        apply(model.leftArm, "left_arm", state, time, weight);
        apply(model.rightLeg, "right_leg", state, time, weight);
        apply(model.leftLeg, "left_leg", state, time, weight);
        // Blockbench bends the torso at the hips; vanilla pivots its torso at the neck.
        // Move the neck/shoulders along the same arc so the body stays attached to the legs.
        float bend = -state.clip().sample("body", "rotation", time).x() * ((float)Math.PI/180);
        float neckY = 12 * (1 - Mth.cos(bend)), neckZ = -12 * Mth.sin(bend);
        model.body.y = Mth.lerp(weight, bodyY, neckY);
        model.body.z = Mth.lerp(weight, bodyZ, neckZ);
        model.head.y = Mth.lerp(weight, headY, neckY);
        model.head.z = Mth.lerp(weight, headZ, neckZ);
        model.rightArm.y = Mth.lerp(weight, rightY, neckY + 2*Mth.cos(bend));
        model.leftArm.y = Mth.lerp(weight, leftY, neckY + 2*Mth.cos(bend));
        model.rightArm.z = Mth.lerp(weight, rightZ, neckZ + 2*Mth.sin(bend));
        model.leftArm.z = Mth.lerp(weight, leftZ, neckZ + 2*Mth.sin(bend));
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.hat.copyFrom(model.head);
    }
    private static void apply(net.minecraft.client.model.geom.ModelPart part, String bone, State state, float time, float weight) {
        var rotation = state.clip().sample(bone, "rotation", time);
        var position = state.clip().sample(bone, "position", time);
        float radians = (float)Math.PI/180;
        part.xRot = Mth.lerp(weight, part.xRot, -rotation.x()*radians);
        part.yRot = Mth.lerp(weight, part.yRot, -rotation.y()*radians);
        part.zRot = Mth.lerp(weight, part.zRot, -rotation.z()*radians);
        // PlayerModel resets these pivots each frame in setupAnim. Position tracks are offsets.
        part.x += position.x()*weight;
        part.y -= position.y()*weight;
        part.z += position.z()*weight;
    }
}
