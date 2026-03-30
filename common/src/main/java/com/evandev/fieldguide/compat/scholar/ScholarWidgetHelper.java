package com.evandev.fieldguide.compat.scholar;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.mortuusars.scholar.client.gui.widget.textbox.TextBox;
import io.github.mortuusars.scholar.client.gui.widget.textbox.display.HorizontalAlignment;
import io.github.mortuusars.scholar.client.gui.widget.textbox.text.FormattedString;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class ScholarWidgetHelper {
    public static AbstractWidget createTextArea(Font font, int x, int y, int width, int height, int maxVisibleLines, int lineHeight, int textColor, boolean scrollable, int maxCharacters, String initialText, Consumer<String> onChanged, Consumer<String> onSpillover) {
        ScholarTextBox textBox = new ScholarTextBox(font, x, y, width, height, initialText, onChanged);
        textBox.setFontColor(textColor | 0xFF000000);
        textBox.setFontUnfocusedColor(textColor | 0xFF000000);

        Predicate<String> validator = str -> {
            if (str.length() > maxCharacters) return false;
            if (!scrollable) {
                int h = font.wordWrapHeight(str, width) + (str.endsWith("\n") ? font.lineHeight : 0);
                return h <= height;
            }
            return true;
        };

        textBox.getEditor().setValidator(validator);
        return textBox;
    }

    public static AbstractWidget createTextField(Font font, int x, int y, int width, int height, String initialText, int textColor, int maxTextWidth, int maxCharacters, Consumer<String> onChanged, boolean centered) {
        ScholarTextBox textBox = new ScholarTextBox(font, x, y, width, height, initialText, onChanged);
        textBox.setFontColor(textColor | 0xFF000000);
        textBox.setFontUnfocusedColor(textColor | 0xFF000000);
        if (centered) textBox.setHorizontalAlignment(HorizontalAlignment.CENTER);

        Predicate<String> validator = str -> str.length() <= maxCharacters && font.width(str) <= maxTextWidth && !str.contains("\n");
        textBox.getEditor().setValidator(validator);
        return textBox;
    }

    private static class ScholarTextBox extends TextBox {
        public ScholarTextBox(Font font, int x, int y, int width, int height, String initialText, Consumer<String> onChanged) {
            super(font, x, y, width, height);
            this.setText(FormattedString.parse(initialText));
            this.setOnTextChanged(formattedString -> onChanged.accept(formattedString.toString()));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isHovered && button == InputConstants.MOUSE_BUTTON_RIGHT) {
                int indexAtMousePos = getDisplayCache().getCharIndexAtPosition(font, (int) (mouseX - getX()), (int) (mouseY - getY()));
                getEditor().selectWord(indexAtMousePos);
                refreshDisplayCache();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}
