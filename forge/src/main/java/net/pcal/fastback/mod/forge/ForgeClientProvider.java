package net.pcal.fastback.mod.forge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.pcal.fastback.logging.UserMessage;

import static java.util.Objects.requireNonNull;
import static net.pcal.fastback.logging.SystemLogger.syslog;
import static net.pcal.fastback.mod.MinecraftProvider.messageToText;

/**
 * Handles client-specific tasks.
 *
 * @author pcal
 * @since 0.16.0
 */
final class ForgeClientProvider extends ForgeCommonProvider {

    // ======================================================================
    // Constants

    private static final long TEXT_TIMEOUT = 10 * 1000;

    // ======================================================================
    // Fields

    //private MinecraftClient client = null;
    private Text hudText;
    private long hudTextTime;
    private final MinecraftClient client;

    public ForgeClientProvider() {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onClientStartupEvent);
        MinecraftForge.EVENT_BUS.addListener(this::onGuiOverlayEvent);
        MinecraftForge.EVENT_BUS.addListener(this::onScreenRenderEvent);
        this.client = requireNonNull(MinecraftClient.getInstance(), "MinecraftClient.getInstance() returned null");
    }

    // ======================================================================
    // Forge Event handlers

    private void onClientStartupEvent(FMLClientSetupEvent event) {
        this.onInitialize();
    }

    private void onGuiOverlayEvent(RenderGameOverlayEvent.Post event) {
        this.renderOverlayText(event.getMatrixStack());
    }

    private void onScreenRenderEvent(ScreenEvent.DrawScreenEvent.Post event) {
        this.renderOverlayText(event.getPoseStack());
    }

    // ======================================================================
    // MinecraftProvider implementation

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public void setHudText(UserMessage userMessage) {
        if (userMessage == null) {
            clearHudText();
        } else {
            this.hudText = messageToText(userMessage); // so the hud renderer can find it
            this.hudTextTime = System.currentTimeMillis();
        }
    }

    @Override
    public void setHudTextForPlayer(UserMessage userMessage, ServerPlayerEntity player) {
    }

    @Override
    public void clearHudText() {
        this.hudText = null;
        // TODO someday it might be nice to bring back the fading text effect.  But getting to it properly
        // clean up 100% of the time is more than I want to deal with right now.
    }

    @Override
    public void setMessageScreenText(UserMessage userMessage) {
        final Text text = messageToText(userMessage);
        this.hudText = text;
        client.player.sendMessage(text, true);
//        final Screen screen = client.currentScreen;
//        if (screen != null) screen.title = text;
    }

    @Override
    void renderOverlayText(final MatrixStack matrixStack) {
        if (this.hudText == null) return;
        // if (!this.client.options.getShowAutosaveIndicator().getValue()) return; FIXME
        if (System.currentTimeMillis() - this.hudTextTime > TEXT_TIMEOUT) {
            // Don't leave it sitting up there forever if we fail to call clearHudText()
            this.hudText = null;
            syslog().debug("hud text timed out.  somebody forgot to clean up");
            return;
        }
        if (client != null) {
            client.player.sendMessage(this.hudText, true);
//            DrawableHelper.drawTextWithShadow(matrixStack, this.client.textRenderer, this.hudText, 2, 2, 1);
        }
    }
}