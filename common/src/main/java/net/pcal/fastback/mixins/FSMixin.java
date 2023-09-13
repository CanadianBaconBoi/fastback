package net.pcal.fastback.mixins;

import org.eclipse.jgit.util.FS;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;

@Mixin(FS.class)
public class FSMixin {
    @Inject(method = "runProcess(Ljava/lang/ProcessBuilder;Ljava/io/OutputStream;Ljava/io/OutputStream;Ljava/io/InputStream;)I",
            at = @At(value = "INVOKE", target = "Ljava/lang/ProcessBuilder;start()Ljava/lang/Process;"),
            remap = false)
    public void runProcess(ProcessBuilder processBuilder, OutputStream outRedirect, OutputStream errRedirect, InputStream inRedirect, CallbackInfoReturnable<Integer> cir) throws IOException, InterruptedException {
        processBuilder.environment().put("PATH", processBuilder.environment().containsKey("PATH") ?
                        Paths.get("", "bin").toAbsolutePath() + ":" + processBuilder.environment().get("PATH")
                        : Paths.get("", "bin").toAbsolutePath().toString());
    }
}
