package com.selectiverendering.mixin.render;

import com.selectiverendering.render.hiding.RetainedTerrain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.3 {
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.SubmitNodeStorage;
//?} else if >=26.2 {
/*import net.minecraft.client.renderer.SubmitNodeStorage;
*///?} else {
/*import net.minecraft.client.renderer.SubmitNodeStorage;
*///?}

// 在半透明实体之前画常驻的半透明地形缓冲（光影下地形没法靠顶点 alpha 半透明）。
//? if >=26.2 {
@Mixin(targets = "net.minecraft.client.renderer.feature.FeatureRenderDispatcher$PreparedFrame")
public class PreparedFrameMixin {
	@Shadow
	private SubmitNodeStorage submitNodeStorage;

	//? if >=26.3 {
	@Inject(method = "executeSolid", at = @At("HEAD"))
	private void selectiveRendering$drawSolidTerrain(RenderPass pass, CallbackInfo ci) {
		RetainedTerrain.drawSolid(pass, this.submitNodeStorage);
	}

	@Inject(method = "executeTranslucent", at = @At("HEAD"))
	private void selectiveRendering$drawTranslucentTerrain(RenderPass pass, CallbackInfo ci) {
		RetainedTerrain.drawTranslucent(pass, this.submitNodeStorage);
	}
	//?} else {
	/*@Inject(method = "executeSolid", at = @At("HEAD"))
	private void selectiveRendering$drawSolidTerrain(CallbackInfo ci) {
		RetainedTerrain.drawSolid(this.submitNodeStorage);
	}

	@Inject(method = "executeTranslucent", at = @At("HEAD"))
	private void selectiveRendering$drawTranslucentTerrain(CallbackInfo ci) {
		RetainedTerrain.drawTranslucent(this.submitNodeStorage);
	}
	*///?}
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.feature.FeatureRenderDispatcher.class)
public class PreparedFrameMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private SubmitNodeStorage submitNodeStorage;

	@Inject(method = "renderSolidFeatures", at = @At("HEAD"))
	private void selectiveRendering$drawSolidTerrain(CallbackInfo ci) {
		RetainedTerrain.drawSolid(this.submitNodeStorage);
	}

	@Inject(method = "renderTranslucentFeatures", at = @At("HEAD"))
	private void selectiveRendering$drawTranslucentTerrain(CallbackInfo ci) {
		RetainedTerrain.drawTranslucent(this.submitNodeStorage);
	}
}
*///?}
