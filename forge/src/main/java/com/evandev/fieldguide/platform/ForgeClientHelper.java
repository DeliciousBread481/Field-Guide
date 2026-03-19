package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.platform.services.IClientHelper;

public class ForgeClientHelper implements IClientHelper {

    @Override
    public void openFieldGuide() {
        FieldGuideClient.openGuide();
    }
}
