package com.shouyun.theartofstockings.mixin;

import com.shouyun.theartofstockings.client.ClientAnimations;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Unique private float[] stockings$basePivots;
    @Unique private net.minecraft.client.model.geom.ModelPart[] stockings$parts() {
        var model = (PlayerModel<?>)(Object)this;
        return new net.minecraft.client.model.geom.ModelPart[]{model.body, model.head, model.rightArm,
                model.leftArm, model.rightLeg, model.leftLeg};
    }
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void stockings$restorePivots(LivingEntity entity, float swing, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        if (stockings$basePivots == null) return;
        var parts = stockings$parts();
        for (int i=0;i<parts.length;i++) {
            parts[i].x = stockings$basePivots[i*3];
            parts[i].y = stockings$basePivots[i*3+1];
            parts[i].z = stockings$basePivots[i*3+2];
        }
        stockings$basePivots = null;
    }
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void stockings$pose(LivingEntity entity, float swing, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        if (ClientAnimations.get(entity) != null) {
            stockings$basePivots = new float[18];
            var parts = stockings$parts();
            for (int i=0;i<parts.length;i++) {
                stockings$basePivots[i*3]=parts[i].x;
                stockings$basePivots[i*3+1]=parts[i].y;
                stockings$basePivots[i*3+2]=parts[i].z;
            }
        }
        ClientAnimations.pose((PlayerModel<?>)(Object)this, entity, age);
    }
}
