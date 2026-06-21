package cn.coostack.cooparticlesapi.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public class CooListeningKeyMapping extends KeyMapping {
    public CooListeningKeyMapping(String name, InputConstants.Type type, int keyCode, String category) {
        super(name, type, keyCode, category);
    }
}
