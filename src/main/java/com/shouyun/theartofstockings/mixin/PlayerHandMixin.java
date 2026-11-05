package com.shouyun.theartofstockings.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shouyun.theartofstockings.client.StockingsLayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerHandMixin {
    @Inject(method="renderHand",at=@At("TAIL"))
    private void stockings$handCloth(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,
                                    ModelPart arm,ModelPart sleeve,CallbackInfo ci) {
        boolean left=arm==((PlayerRenderer)(Object)this).getModel().leftArm;
        StockingsLayer.renderFirstPerson(pose,buffers,light,player,arm,left);
    }
}
