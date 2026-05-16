package aurick.opsec.mod.mixin.client;

//? if >=1.20.5 {
import aurick.opsec.mod.protection.OpsecComponentCodec;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(ComponentSerialization.class)
public class ComponentSerializationMixin {

    @WrapOperation(
        method = "<clinit>",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<Component> opsec$wrapRecursive(String name,
            Function<Codec<Component>, Codec<Component>> body,
            Operation<Codec<Component>> original) {
        return new OpsecComponentCodec(original.call(name, body));
    }
}
//?} else {
/*
import aurick.opsec.mod.protection.OpsecFromPacketAccess;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

// Pre-1.20.5: no ComponentSerialization codec. Components are parsed via the Gson
// adapter Component$Serializer.deserialize, called from FriendlyByteBuf.readComponent
// on the netty I/O thread (so PacketContext is not set yet — it goes true later on
// the game thread inside PacketUtilsMixin). Tag every multiplayer-context deserialize
// return tree so TranslatableContentsMixin / KeybindContentsMixin's per-instance
// opsec$fromPacket flag is set before the component is ever rendered.
@Mixin(Component.Serializer.class)
public class ComponentSerializationMixin {

    @Inject(
        method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/network/chat/MutableComponent;",
        at = @At("RETURN")
    )
    private void opsec$tagDeserialized(JsonElement element, Type type, JsonDeserializationContext context,
            CallbackInfoReturnable<MutableComponent> cir) {
        MutableComponent result = cir.getReturnValue();
        if (result == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.hasSingleplayerServer()) return;
        opsec$markTree(result);
    }

    @Unique
    private static void opsec$markTree(Component component) {
        if (component == null) return;
        ComponentContents contents = component.getContents();
        if (contents instanceof OpsecFromPacketAccess access) access.opsec$setFromPacket();
        if (contents instanceof TranslatableContents tc) {
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component argComp) opsec$markTree(argComp);
            }
        }
        for (Component sibling : component.getSiblings()) opsec$markTree(sibling);
    }
}
*///?}
