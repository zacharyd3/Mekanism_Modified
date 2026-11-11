package mekwasher;

import mekanism.api.text.ILangEntry;

public enum MWLang implements ILangEntry {
    DESCRIPTION_BULK_WASHER("description", "bulk_washer"),
    DESCRIPTION_ELECTRIC_DEPLOYER("description", "electric_deployer");

    private final String key;

    MWLang(String type, String path) {
        this.key = type + "." + MekWasher.MOD_ID + "." + path;
    }

    @Override
    public String getTranslationKey() {
        return key;
    }
}
