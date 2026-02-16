package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.widget.TabButton;
import com.evandev.fieldguide.data.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class BookScreen extends Screen {
    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 200;
    private static final int PAGE_WIDTH = 122;
    private static final int PAGE_HEIGHT = 164;

    private static final int TAB_WIDTH = 24;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_GAP = 0;
    private static final int TAB_Y_OFFSET = 29;

    private final List<Category> sortedCategories = new ArrayList<>();
    protected Bounds bounds;
    protected Bounds leftPageBounds;
    protected Bounds rightPageBounds;
    private Category selectedCategory;

    protected BookScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        this.bounds = new Bounds((this.width - BG_WIDTH) / 2, (this.height - BG_HEIGHT) / 2, BG_WIDTH, BG_HEIGHT);
        this.leftPageBounds = new Bounds(this.bounds.left() + 22, this.bounds.top() + 19, PAGE_WIDTH, PAGE_HEIGHT);
        this.rightPageBounds = new Bounds(this.leftPageBounds.right() + 13, leftPageBounds.top(), PAGE_WIDTH, PAGE_HEIGHT);

        initCategories();
    }

    private void initCategories() {
        // Sort categories
        this.sortedCategories.clear();
        for (Category cat : ClientFieldGuideManager.getCategories().values()) {
            List<Object> entries = ClientFieldGuideManager.getInstance().getEntriesForCategory(cat);
            if (entries != null && !entries.isEmpty()) {
                this.sortedCategories.add(cat);
            }
        }
        this.sortedCategories.sort(Comparator.comparingInt(Category::getSortIndex)
                .thenComparing(c -> c.getId().getPath()));

        // Add tabs
        int startY = this.bounds.top() + TAB_Y_OFFSET;
        int xPos = this.bounds.left() - 7;
        for (int i = 0; i < sortedCategories.size(); i++) {
            Category category = sortedCategories.get(i);
            int yPos = startY + (i * (TAB_HEIGHT + TAB_GAP));

            TabButton tab = new TabButton(xPos, yPos, TAB_WIDTH, TAB_HEIGHT, category, this);
            this.addRenderableWidget(tab);
        }
    }

    abstract public void onTabClick(Category category);

    @Override
    public void onClose() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 1.0F, 1.0F));
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (FieldGuideClient.OPEN_GUIDE_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public List<Category> getSortedCategories() {
        return this.sortedCategories;
    }

    public Category getSelectedCategory() {
        return this.selectedCategory;
    }

    public void setSelectedCategory(Category category) {
        this.selectedCategory = category;
    }
}