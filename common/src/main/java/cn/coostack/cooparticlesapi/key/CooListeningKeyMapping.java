package cn.coostack.cooparticlesapi.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * 保留只监听绑定的类型标记；物理状态由 CooKeyBindingManager 统一采样。
 */
public class CooListeningKeyMapping extends KeyMapping {
    /**
     * 在加载器按键注册阶段创建绑定。
     *
     * @param name 按键翻译键
     * @param type 键盘或鼠标输入类型
     * @param keyCode 对应输入类型的 SDL 默认键编号
     * @param category 已由管理器去重注册的原版分类
     */
    public CooListeningKeyMapping(String name, InputConstants.Type type, int keyCode, Category category) {
        super(name, type, keyCode, category);
    }
}
